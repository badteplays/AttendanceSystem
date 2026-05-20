// ─── No login needed — local admin tool ─────────────────────
const API = '/api';
let allUsers = [];
let currentFilter = 'all';

// ─── API helper (no auth token needed) ──────────────────────
async function apiCall(method, path, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(`${API}${path}`, opts);
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}

// ─── Load Users on startup ──────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  loadUsers();
});

async function loadUsers() {
  try {
    allUsers = await apiCall('GET', '/users');
    updateStats();
    renderUsers();
  } catch (err) {
    showToast('Failed to load users: ' + err.message, 'error');
  }
}

function updateStats() {
  const students = allUsers.filter(u => u.role === 'student');
  const teachers = allUsers.filter(u => u.role === 'teacher');
  document.getElementById('statTotal').textContent = allUsers.length;
  document.getElementById('statStudents').textContent = students.length;
  document.getElementById('statTeachers').textContent = teachers.length;
}

function renderUsers() {
  const tbody = document.getElementById('usersTableBody');
  const search = document.getElementById('searchInput').value.toLowerCase();

  let filtered = allUsers;
  if (currentFilter !== 'all') {
    filtered = filtered.filter(u => u.role === currentFilter);
  }
  if (search) {
    filtered = filtered.filter(u =>
      (u.name || '').toLowerCase().includes(search) ||
      (u.email || '').toLowerCase().includes(search)
    );
  }

  if (filtered.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5">
      <div class="empty-state">
        <div class="empty-state-icon">🔍</div>
        <div class="empty-state-text">No users found</div>
      </div>
    </td></tr>`;
    return;
  }

  tbody.innerHTML = filtered.map(u => {
    const initials = (u.name || 'U')[0].toUpperCase();
    const roleClass = u.role === 'teacher' ? 'teacher' : 'student';
    const badgeClass = u.role === 'teacher' ? 'badge-teacher' : 'badge-student';
    const detail = u.role === 'teacher' ? (u.department || '—') : (u.section || '—');
    const date = u.createdAt ? new Date(u.createdAt).toLocaleDateString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric'
    }) : '—';

    return `<tr>
      <td>
        <div class="user-cell">
          <div class="user-avatar ${roleClass}">${initials}</div>
          <div>
            <div class="user-name-text">${escHtml(u.name || 'Unknown')}</div>
            <div class="user-email-text">${escHtml(u.email)}</div>
          </div>
        </div>
      </td>
      <td><span class="badge ${badgeClass}">${u.role}</span></td>
      <td>${escHtml(detail)}</td>
      <td style="color: var(--text-muted); font-size: 0.8rem;">${date}</td>
      <td>
        <div class="actions-cell">
          <button class="action-btn" title="Edit" onclick='openEditModal(${JSON.stringify(u).replace(/'/g, "&#39;")})'>✏️</button>
          <button class="action-btn" title="Change Password" onclick='openPasswordModal("${u.uid}", "${escAttr(u.name)}")'>🔑</button>
          <button class="action-btn danger" title="Delete" onclick='openDeleteModal("${u.uid}", "${escAttr(u.name)}")'>🗑️</button>
        </div>
      </td>
    </tr>`;
  }).join('');
}

function escHtml(s) {
  const d = document.createElement('div');
  d.textContent = s || '';
  return d.innerHTML;
}

function escAttr(s) {
  return (s || '').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// ─── Filters ────────────────────────────────────────────────
function setFilter(f) {
  currentFilter = f;
  document.querySelectorAll('.filter-btn').forEach(b => {
    b.classList.toggle('active', b.dataset.filter === f);
  });
  renderUsers();
}

function filterUsers() { renderUsers(); }

// ─── Pages ──────────────────────────────────────────────────
function showPage(page) {
  document.querySelectorAll('.page-section').forEach(s => s.classList.remove('active'));
  
  let sectionId = 'pageUsers';
  if (page === 'create') sectionId = 'pageCreate';
  if (page === 'schedules') sectionId = 'pageSchedules';
  
  document.getElementById(sectionId).classList.add('active');
  
  document.querySelectorAll('.nav-item').forEach(n => {
    n.classList.toggle('active', n.dataset.page === page);
  });

  if (page === 'schedules') {
    renderTeachersForSchedules();
  }
}

// ─── Create Account ─────────────────────────────────────────
function toggleCreateFields() {
  const role = document.getElementById('createRole').value;
  document.getElementById('createSectionGroup').classList.toggle('hidden', role !== 'student');
  document.getElementById('createSectionNumGroup').classList.toggle('hidden', role !== 'student');
  document.getElementById('createDeptGroup').classList.toggle('hidden', role !== 'teacher');
}

async function handleCreateUser(e) {
  e.preventDefault();
  const btn = document.getElementById('createBtn');
  btn.disabled = true;
  btn.innerHTML = '<div class="spinner"></div>';

  const role = document.getElementById('createRole').value;
  const body = {
    name: document.getElementById('createName').value.trim(),
    email: document.getElementById('createEmail').value.trim(),
    password: document.getElementById('createPassword').value,
    role,
  };
  if (role === 'student') {
    const strand = document.getElementById('createSection').value;
    const num = document.getElementById('createSectionNum').value.trim();
    body.section = num ? `${strand}-${num}` : strand;
  }
  if (role === 'teacher') body.department = document.getElementById('createDept').value.trim();

  try {
    await apiCall('POST', '/users', body);
    showToast(`${role.charAt(0).toUpperCase() + role.slice(1)} account created!`, 'success');
    document.getElementById('createForm').reset();
    toggleCreateFields();
    loadUsers();
    showPage('users');
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Create Account';
  }
}

// ─── Edit User ──────────────────────────────────────────────
function openEditModal(user) {
  document.getElementById('editUid').value = user.uid;
  document.getElementById('editName').value = user.name || '';
  document.getElementById('editEmail').value = user.email || '';
  document.getElementById('editRole').value = user.role || 'student';
  
  // Parse section into strand + number (e.g. "BSIT-101" → "BSIT" + "101")
  const section = user.section || '';
  const sectionParts = section.match(/^(.+?)(?:-([^-]+))?$/);
  const strandSelect = document.getElementById('editSection');
  const sectionNum = document.getElementById('editSectionNum');
  
  // Try to match strand from dropdown options
  const strandValue = sectionParts ? sectionParts[1] : section;
  const numValue = sectionParts && sectionParts[2] ? sectionParts[2] : '';
  
  // Check if strand exists in dropdown
  const optionExists = Array.from(strandSelect.options).some(o => o.value === strandValue);
  if (optionExists) {
    strandSelect.value = strandValue;
  } else {
    strandSelect.value = '';
  }
  sectionNum.value = numValue;
  
  document.getElementById('editDept').value = user.department || '';
  toggleEditFields();
  openModal('editModal');
}

function toggleEditFields() {
  const role = document.getElementById('editRole').value;
  document.getElementById('editSectionGroup').classList.toggle('hidden', role !== 'student');
  document.getElementById('editSectionNumGroup').classList.toggle('hidden', role !== 'student');
  document.getElementById('editDeptGroup').classList.toggle('hidden', role !== 'teacher');
}

async function handleEditUser() {
  const btn = document.getElementById('editSaveBtn');
  btn.disabled = true;
  btn.innerHTML = '<div class="spinner"></div>';

  const uid = document.getElementById('editUid').value;
  const role = document.getElementById('editRole').value;
  const body = {
    name: document.getElementById('editName').value.trim(),
    role,
  };
  if (role === 'student') {
    const strand = document.getElementById('editSection').value;
    const num = document.getElementById('editSectionNum').value.trim();
    body.section = num ? `${strand}-${num}` : strand;
  }
  if (role === 'teacher') body.department = document.getElementById('editDept').value.trim();

  try {
    await apiCall('PUT', `/users/${uid}`, body);
    showToast('User updated', 'success');
    closeModal('editModal');
    loadUsers();
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Save Changes';
  }
}

// ─── Change Password ────────────────────────────────────────
function openPasswordModal(uid, name) {
  document.getElementById('passwordUid').value = uid;
  document.getElementById('passwordUserName').textContent = name;
  document.getElementById('newPassword').value = '';
  openModal('passwordModal');
}

async function handleChangePassword() {
  const btn = document.getElementById('passwordSaveBtn');
  btn.disabled = true;
  btn.innerHTML = '<div class="spinner"></div>';

  const uid = document.getElementById('passwordUid').value;
  const password = document.getElementById('newPassword').value;

  if (!password || password.length < 6) {
    showToast('Password must be at least 6 characters', 'error');
    btn.disabled = false;
    btn.textContent = 'Update Password';
    return;
  }

  try {
    await apiCall('PUT', `/users/${uid}/password`, { password });
    showToast('Password updated', 'success');
    closeModal('passwordModal');
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Update Password';
  }
}

// ─── Delete User ────────────────────────────────────────────
function openDeleteModal(uid, name) {
  document.getElementById('deleteUid').value = uid;
  document.getElementById('deleteUserName').textContent = name;
  openModal('deleteModal');
}

async function handleDeleteUser() {
  const btn = document.getElementById('deleteSaveBtn');
  btn.disabled = true;
  btn.innerHTML = '<div class="spinner"></div>';

  const uid = document.getElementById('deleteUid').value;

  try {
    await apiCall('DELETE', `/users/${uid}`);
    showToast('User deleted', 'success');
    closeModal('deleteModal');
    loadUsers();
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Delete User';
  }
}

// ─── Modals ─────────────────────────────────────────────────
function openModal(id) {
  document.getElementById(id).classList.add('active');
}

function closeModal(id) {
  document.getElementById(id).classList.remove('active');
}

// Close modal on overlay click
document.querySelectorAll('.modal-overlay').forEach(overlay => {
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) overlay.classList.remove('active');
  });
});

// ─── Toasts ─────────────────────────────────────────────────
function showToast(message, type = 'success') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `
    <span class="toast-icon">${type === 'success' ? '✓' : '✕'}</span>
    <span>${escHtml(message)}</span>
  `;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(20px)';
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}

// ─── Teacher Schedules Management ────────────────────────────
let selectedTeacherId = null;

function renderTeachersForSchedules() {
  const container = document.getElementById('teachersList');
  const teachers = allUsers.filter(u => u.role === 'teacher');
  const search = document.getElementById('teacherSearchInput').value.toLowerCase();

  let filtered = teachers;
  if (search) {
    filtered = teachers.filter(u =>
      (u.name || '').toLowerCase().includes(search) ||
      (u.department || '').toLowerCase().includes(search)
    );
  }

  if (filtered.length === 0) {
    container.innerHTML = `<div class="empty-state">
      <div class="empty-state-icon">🔍</div>
      <div class="empty-state-text">No teachers found</div>
    </div>`;
    return;
  }

  container.innerHTML = filtered.map(t => {
    const initials = (t.name || 'T')[0].toUpperCase();
    const activeClass = selectedTeacherId === t.uid ? 'active' : '';
    return `
      <div class="teacher-item ${activeClass}" onclick="selectTeacher('${t.uid}')">
        <div class="teacher-item-avatar">${initials}</div>
        <div class="teacher-item-info">
          <div class="teacher-item-name">${escHtml(t.name)}</div>
          <div class="teacher-item-dept">${escHtml(t.department || 'No Department')}</div>
        </div>
      </div>
    `;
  }).join('');
}

function filterTeachersList() {
  renderTeachersForSchedules();
}

async function selectTeacher(uid) {
  selectedTeacherId = uid;
  
  // Highlight selected teacher in left panel list
  document.querySelectorAll('.teacher-item').forEach(item => {
    item.classList.remove('active');
  });
  renderTeachersForSchedules();

  const teacher = allUsers.find(u => u.uid === uid);
  const detailContainer = document.getElementById('teacherScheduleDetail');
  
  detailContainer.innerHTML = `
    <div style="display: flex; justify-content: center; align-items: center; min-height: 200px;">
      <div class="spinner"></div>
    </div>
  `;

  try {
    const schedules = await apiCall('GET', `/schedules?teacherId=${uid}`);
    renderTeacherSchedules(teacher, schedules);
  } catch (err) {
    detailContainer.innerHTML = `
      <div class="empty-state">
        <div class="empty-state-icon">✕</div>
        <div class="empty-state-text">Failed to load schedules: ${escHtml(err.message)}</div>
      </div>
    `;
  }
}

function renderTeacherSchedules(teacher, schedules) {
  const container = document.getElementById('teacherScheduleDetail');
  
  const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  
  const daysHtml = days.map(day => {
    const daySchedules = schedules
      .filter(s => s.day === day)
      .sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''));

    const slotsHtml = daySchedules.length > 0 
      ? daySchedules.map(s => {
          // format times for display e.g. "08:00" to "8:00 AM"
          const displayTime = `${formatTime12(s.startTime)} – ${formatTime12(s.endTime)}`;
          const slotDataJson = JSON.stringify(s).replace(/'/g, "&#39;");
          return `
            <div class="slot-card">
              <div class="slot-subject">${escHtml(s.subject)}</div>
              <div class="slot-meta">
                <span class="slot-time">⏰ ${displayTime}</span>
                <span class="slot-room-section">${escHtml(s.room)} • ${escHtml(s.section)}</span>
              </div>
              <div class="slot-actions">
                <button class="slot-action-btn" title="Edit Class Slot" onclick='openEditScheduleModal(${slotDataJson})'>✏️</button>
                <button class="slot-action-btn danger" title="Delete Class Slot" onclick="handleDeleteSchedule('${s.id}')">🗑️</button>
              </div>
            </div>
          `;
        }).join('')
      : `<div class="no-slots">No classes scheduled</div>`;

    return `
      <div class="day-column">
        <div class="day-header">
          <span class="day-name">${day}</span>
          <button class="add-slot-btn" onclick="openAddScheduleModal('${day}')">+ Add Slot</button>
        </div>
        <div class="slots-list">
          ${slotsHtml}
        </div>
      </div>
    `;
  }).join('');

  container.innerHTML = `
    <div class="detail-header">
      <div>
        <h2 class="detail-title">${escHtml(teacher.name)}</h2>
        <p class="detail-subtitle">Department: ${escHtml(teacher.department || 'N/A')} • ${schedules.length} total classes</p>
      </div>
    </div>
    <div class="days-grid">
      ${daysHtml}
    </div>
  `;
}

function formatTime12(time24) {
  if (!time24) return '';
  try {
    const parts = time24.split(':');
    const hours = parseInt(parts[0], 10);
    const minutes = parts[1];
    const ampm = hours >= 12 ? 'PM' : 'AM';
    const hours12 = hours % 12 || 12;
    return `${hours12}:${minutes} ${ampm}`;
  } catch (e) {
    return time24;
  }
}

function openAddScheduleModal(day) {
  document.getElementById('scheduleModalTitle').textContent = 'Add Class Slot';
  document.getElementById('schedId').value = '';
  document.getElementById('schedTeacherId').value = selectedTeacherId;
  document.getElementById('schedDay').value = day;
  document.getElementById('schedSubject').value = '';
  document.getElementById('schedRoom').value = '';
  document.getElementById('schedStartTime').value = '08:00';
  document.getElementById('schedEndTime').value = '10:00';
  
  // Try to default strand
  const teacher = allUsers.find(u => u.uid === selectedTeacherId);
  if (teacher && teacher.department === 'ICT') {
    document.getElementById('schedStrand').value = 'MAWD';
  } else if (teacher && teacher.department === 'Science') {
    document.getElementById('schedStrand').value = 'STEM';
  } else {
    document.getElementById('schedStrand').value = 'STEM';
  }
  document.getElementById('schedSectionNum').value = '301';

  openModal('scheduleModal');
}

function openEditScheduleModal(slot) {
  document.getElementById('scheduleModalTitle').textContent = 'Edit Class Slot';
  document.getElementById('schedId').value = slot.id;
  document.getElementById('schedTeacherId').value = slot.teacherId;
  document.getElementById('schedDay').value = slot.day;
  document.getElementById('schedSubject').value = slot.subject;
  document.getElementById('schedRoom').value = slot.room;
  document.getElementById('schedStartTime').value = slot.startTime;
  document.getElementById('schedEndTime').value = slot.endTime;

  // Parse section e.g. "MAWD-301" into strand and section number
  const section = slot.section || '';
  const parts = section.split('-');
  if (parts.length === 2) {
    document.getElementById('schedStrand').value = parts[0];
    document.getElementById('schedSectionNum').value = parts[1];
  } else {
    document.getElementById('schedStrand').value = 'STEM';
    document.getElementById('schedSectionNum').value = section;
  }

  openModal('scheduleModal');
}

async function handleSaveSchedule(e) {
  e.preventDefault();
  const btn = document.getElementById('schedSaveBtn');
  btn.disabled = true;
  btn.innerHTML = '<div class="spinner"></div>';

  const id = document.getElementById('schedId').value;
  const teacherId = document.getElementById('schedTeacherId').value;
  const day = document.getElementById('schedDay').value;
  const subject = document.getElementById('schedSubject').value.trim();
  const room = document.getElementById('schedRoom').value.trim();
  const startTime = document.getElementById('schedStartTime').value;
  const endTime = document.getElementById('schedEndTime').value;
  
  const strand = document.getElementById('schedStrand').value;
  const num = document.getElementById('schedSectionNum').value.trim();
  const section = `${strand}-${num}`;

  const body = {
    teacherId,
    day,
    subject,
    room,
    startTime,
    endTime,
    section
  };

  try {
    if (id) {
      // Edit schedule slot
      await apiCall('PUT', `/schedules/${id}`, body);
      showToast('Schedule slot updated', 'success');
    } else {
      // Create new schedule slot
      await apiCall('POST', `/schedules`, body);
      showToast('Schedule slot created', 'success');
    }
    closeModal('scheduleModal');
    
    // Refresh schedule detail
    const teacher = allUsers.find(u => u.uid === teacherId);
    const schedules = await apiCall('GET', `/schedules?teacherId=${teacherId}`);
    renderTeacherSchedules(teacher, schedules);
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Save Class Slot';
  }
}

async function handleDeleteSchedule(id) {
  if (!confirm('Are you sure you want to delete this class slot?')) {
    return;
  }

  try {
    await apiCall('DELETE', `/schedules/${id}`);
    showToast('Schedule slot deleted', 'success');
    
    // Refresh schedule detail
    const teacherId = selectedTeacherId;
    const teacher = allUsers.find(u => u.uid === teacherId);
    const schedules = await apiCall('GET', `/schedules?teacherId=${teacherId}`);
    renderTeacherSchedules(teacher, schedules);
  } catch (err) {
    showToast(err.message, 'error');
  }
}
