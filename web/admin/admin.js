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
  document.getElementById(page === 'users' ? 'pageUsers' : 'pageCreate').classList.add('active');
  document.querySelectorAll('.nav-item').forEach(n => {
    n.classList.toggle('active', n.dataset.page === page);
  });
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
