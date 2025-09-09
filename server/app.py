from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from flask_cors import CORS
import bcrypt
import jwt
from datetime import datetime, timedelta
import os
from dotenv import load_dotenv
import requests

load_dotenv()

app = Flask(__name__)
CORS(app, resources={r"/api/*": {"origins": "*"}})  # Allow all origins for testing

# Database configuration
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///attendance.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['SECRET_KEY'] = os.getenv('SECRET_KEY', 'your-secret-key')
app.config['JWT_ACCESS_TOKEN_EXPIRES'] = timedelta(hours=1)

db = SQLAlchemy(app)

# Function to get public IP
def get_public_ip():
    try:
        response = requests.get('https://api.ipify.org?format=json')
        return response.json()['ip']
    except:
        return None

# Models
class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    password = db.Column(db.String(120), nullable=False)
    user_type = db.Column(db.String(20), nullable=False)  # 'Teacher' or 'Student'
    name = db.Column(db.String(100))
    student_id = db.Column(db.String(20))
    section = db.Column(db.String(20))  # For students
    login_attempts = db.Column(db.Integer, default=0)
    last_attempt = db.Column(db.DateTime)
    is_locked = db.Column(db.Boolean, default=False)

class Schedule(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    teacher_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    section = db.Column(db.String(20), nullable=False)
    start_time = db.Column(db.DateTime, nullable=False)
    end_time = db.Column(db.DateTime, nullable=False)
    subject = db.Column(db.String(100), nullable=False)
    qr_code = db.Column(db.String(100))
    qr_expiry = db.Column(db.DateTime)

class Attendance(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    student_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    schedule_id = db.Column(db.Integer, db.ForeignKey('schedule.id'), nullable=False)
    timestamp = db.Column(db.DateTime, default=datetime.utcnow)
    status = db.Column(db.String(20), default='Present')

# Routes
@app.route('/api/register', methods=['POST'])
def register():
    data = request.get_json()
    
    # Check if user already exists
    if User.query.filter_by(username=data['username']).first():
        return jsonify({'error': 'Username already exists'}), 400
    
    # Hash password
    hashed_password = bcrypt.hashpw(data['password'].encode('utf-8'), bcrypt.gensalt())
    
    # Create new user
    new_user = User(
        username=data['username'],
        password=hashed_password.decode('utf-8'),
        user_type=data['user_type'],
        name=data.get('name'),
        student_id=data.get('student_id'),
        section=data.get('section')
    )
    
    db.session.add(new_user)
    db.session.commit()
    
    return jsonify({'message': 'User registered successfully'}), 201

@app.route('/api/login', methods=['POST'])
def login():
    data = request.get_json()
    user = User.query.filter_by(username=data['username']).first()
    
    if not user:
        return jsonify({'error': 'User not found'}), 404
    
    # Check if account is locked
    if user.is_locked:
        if datetime.utcnow() - user.last_attempt < timedelta(minutes=15):
            return jsonify({'error': 'Account is locked. Try again later'}), 403
        else:
            user.is_locked = False
            user.login_attempts = 0
    
    # Verify password
    if bcrypt.checkpw(data['password'].encode('utf-8'), user.password.encode('utf-8')):
        # Reset login attempts on successful login
        user.login_attempts = 0
        user.last_attempt = None
        db.session.commit()
        
        # Generate JWT token
        token = jwt.encode({
            'user_id': user.id,
            'exp': datetime.utcnow() + timedelta(days=1)
        }, app.config['SECRET_KEY'])
        
        return jsonify({
            'token': token,
            'user_type': user.user_type,
            'name': user.name,
            'section': user.section
        })
    else:
        # Increment login attempts
        user.login_attempts += 1
        user.last_attempt = datetime.utcnow()
        if user.login_attempts >= 5:
            user.is_locked = True
        db.session.commit()
        
        return jsonify({'error': 'Invalid password'}), 401

@app.route('/api/schedules', methods=['POST'])
def create_schedule():
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({'error': 'Token is missing'}), 401
    
    try:
        data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
        user = User.query.get(data['user_id'])
        
        if not user or user.user_type != 'Teacher':
            return jsonify({'error': 'Unauthorized'}), 403
        
        schedule_data = request.get_json()
        new_schedule = Schedule(
            teacher_id=user.id,
            section=schedule_data['section'],
            start_time=datetime.fromisoformat(schedule_data['start_time']),
            end_time=datetime.fromisoformat(schedule_data['end_time']),
            subject=schedule_data['subject']
        )
        
        db.session.add(new_schedule)
        db.session.commit()
        
        return jsonify({'message': 'Schedule created successfully'}), 201
    except jwt.ExpiredSignatureError:
        return jsonify({'error': 'Token has expired'}), 401
    except jwt.InvalidTokenError:
        return jsonify({'error': 'Invalid token'}), 401

@app.route('/api/schedules', methods=['GET'])
def get_schedules():
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({'error': 'Token is missing'}), 401
    
    try:
        data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
        user = User.query.get(data['user_id'])
        
        if not user:
            return jsonify({'error': 'User not found'}), 404
        
        if user.user_type == 'Teacher':
            schedules = Schedule.query.filter_by(teacher_id=user.id).all()
        else:
            schedules = Schedule.query.filter_by(section=user.section).all()
        
        return jsonify([{
            'id': s.id,
            'section': s.section,
            'start_time': s.start_time.isoformat(),
            'end_time': s.end_time.isoformat(),
            'subject': s.subject,
            'qr_code': s.qr_code,
            'qr_expiry': s.qr_expiry.isoformat() if s.qr_expiry else None
        } for s in schedules])
    except jwt.ExpiredSignatureError:
        return jsonify({'error': 'Token has expired'}), 401
    except jwt.InvalidTokenError:
        return jsonify({'error': 'Invalid token'}), 401

@app.route('/api/generate_qr', methods=['POST'])
def generate_qr():
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({'error': 'Token is missing'}), 401
    
    try:
        data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
        user = User.query.get(data['user_id'])
        
        if not user or user.user_type != 'Teacher':
            return jsonify({'error': 'Unauthorized'}), 403
        
        schedule_id = request.get_json()['schedule_id']
        schedule = Schedule.query.get(schedule_id)
        
        if not schedule or schedule.teacher_id != user.id:
            return jsonify({'error': 'Schedule not found'}), 404
        
        # Generate unique QR code
        qr_data = f"attendance-{schedule.id}-{datetime.utcnow().timestamp()}"
        schedule.qr_code = qr_data
        schedule.qr_expiry = schedule.end_time
        
        db.session.commit()
        
        return jsonify({
            'qr_code': qr_data,
            'expiry': schedule.qr_expiry.isoformat()
        })
    except jwt.ExpiredSignatureError:
        return jsonify({'error': 'Token has expired'}), 401
    except jwt.InvalidTokenError:
        return jsonify({'error': 'Invalid token'}), 401

@app.route('/api/attendance', methods=['POST'])
def record_attendance():
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({'error': 'Token is missing'}), 401
    
    try:
        data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
        user = User.query.get(data['user_id'])
        
        if not user or user.user_type != 'Student':
            return jsonify({'error': 'Unauthorized'}), 403
        
        qr_data = request.get_json()['qr_code']
        schedule = Schedule.query.filter_by(qr_code=qr_data).first()
        
        if not schedule:
            return jsonify({'error': 'Invalid QR code'}), 400
        
        if datetime.utcnow() > schedule.qr_expiry:
            return jsonify({'error': 'QR code has expired'}), 400
        
        # Check if already marked attendance
        existing = Attendance.query.filter_by(
            student_id=user.id,
            schedule_id=schedule.id
        ).first()
        
        if existing:
            return jsonify({'error': 'Attendance already marked'}), 400
        
        attendance = Attendance(
            student_id=user.id,
            schedule_id=schedule.id,
            status='Present'
        )
        
        db.session.add(attendance)
        db.session.commit()
        
        return jsonify({
            'message': 'Attendance recorded successfully',
            'schedule': {
                'subject': schedule.subject,
                'teacher': User.query.get(schedule.teacher_id).name
            }
        })
    except jwt.ExpiredSignatureError:
        return jsonify({'error': 'Token has expired'}), 401
    except jwt.InvalidTokenError:
        return jsonify({'error': 'Invalid token'}), 401

@app.route('/api/attendance/<int:schedule_id>', methods=['GET'])
def get_attendance(schedule_id):
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({'error': 'Token is missing'}), 401
    
    try:
        data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
        user = User.query.get(data['user_id'])
        
        if not user or user.user_type != 'Teacher':
            return jsonify({'error': 'Unauthorized'}), 403
        
        schedule = Schedule.query.get(schedule_id)
        if not schedule or schedule.teacher_id != user.id:
            return jsonify({'error': 'Schedule not found'}), 404
        
        attendances = Attendance.query.filter_by(schedule_id=schedule_id).all()
        return jsonify([{
            'student_id': a.student_id,
            'student_name': User.query.get(a.student_id).name,
            'timestamp': a.timestamp.isoformat(),
            'status': a.status
        } for a in attendances])
    except jwt.ExpiredSignatureError:
        return jsonify({'error': 'Token has expired'}), 401
    except jwt.InvalidTokenError:
        return jsonify({'error': 'Invalid token'}), 401

@app.route('/api/attendance/history', methods=['GET'])
def get_attendance_history():
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({'error': 'Token is missing'}), 401
    
    try:
        data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
        user = User.query.get(data['user_id'])
        
        if not user:
            return jsonify({'error': 'User not found'}), 404
        
        if user.user_type == 'Student':
            attendances = Attendance.query.filter_by(student_id=user.id).all()
        else:
            attendances = Attendance.query.join(Schedule).filter(
                Schedule.teacher_id == user.id
            ).all()
        
        return jsonify([{
            'schedule_id': a.schedule_id,
            'subject': Schedule.query.get(a.schedule_id).subject,
            'teacher_name': User.query.get(Schedule.query.get(a.schedule_id).teacher_id).name,
            'timestamp': a.timestamp.isoformat(),
            'status': a.status
        } for a in attendances])
    except jwt.ExpiredSignatureError:
        return jsonify({'error': 'Token has expired'}), 401
    except jwt.InvalidTokenError:
        return jsonify({'error': 'Invalid token'}), 401

@app.route('/api/submit_data', methods=['POST'])
def submit_data():
    try:
        data = request.get_json()
        
        # Here you can process the data as needed
        # For example, save it to a file or database
        with open('submitted_data.txt', 'a') as f:
            f.write(f"{datetime.now().isoformat()}: {data}\n")
        
        return jsonify({'message': 'Data received successfully'}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    
    # Get and display public IP
    public_ip = get_public_ip()
    if public_ip:
        print(f"\nServer will be available at: http://{public_ip}:5000")
        print("Share this IP with your testers")
    else:
        print("\nCould not determine public IP. Testers may not be able to connect.")
    
    try:
        app.run(host='0.0.0.0', port=5000, debug=True)
    except Exception as e:
        print(f"\nError starting server: {str(e)}")
        print("Check if the port is available and not blocked by your mobile carrier.") 