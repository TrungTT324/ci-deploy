const loginScreen = document.getElementById('login');
const dashboardScreen = document.getElementById('dashboard');
const pinInput = document.getElementById('pin');
const errorMsg = document.getElementById('error');
const btnSubmit = document.getElementById('btnSubmit');
const btnLogout = document.getElementById('btnLogout');

// Check if already authenticated and check server auth config
document.addEventListener('DOMContentLoaded', async () => {
    try {
        const response = await fetch('/api/config');
        if (response.ok) {
            const config = await response.json();
            if (!config.requireAuthen) {
                showDashboard();
                return;
            }
        }
    } catch (e) {
        console.error("Failed to fetch web config:", e);
    }

    const token = localStorage.getItem('ci_deploy_token');
    if (token) {
        showDashboard();
    } else {
        showLogin();
    }
});

// Automatically submit when 4 digits are typed
pinInput.addEventListener('input', (e) => {
    pinInput.value = pinInput.value.replace(/[^0-9]/g, ''); // Numeric only
    if (pinInput.value.length === 4) {
        // Short timeout to let the user see the number typed
        setTimeout(submitAuth, 200);
    }
});

btnSubmit.addEventListener('click', submitAuth);

btnLogout.addEventListener('click', () => {
    localStorage.removeItem('ci_deploy_token');
    showLogin();
});

async function submitAuth() {
    const pin = pinInput.value;
    if (!pin) return;

    errorMsg.style.display = 'none';

    try {
        const response = await fetch(`/api/auth?pin=${pin}`);
        if (response.ok) {
            const data = await response.json();
            localStorage.setItem('ci_deploy_token', data.token);
            showDashboard();
        } else {
            showError();
        }
    } catch (err) {
        showError("Network Error. Cannot connect to device.");
    }
}

function showError(msg = "Invalid PIN. Please try again.") {
    errorMsg.innerText = msg;
    errorMsg.style.display = 'block';
    pinInput.value = '';
    pinInput.focus();
}

function showDashboard() {
    loginScreen.style.display = 'none';
    dashboardScreen.style.display = 'block';
}

function showLogin() {
    dashboardScreen.style.display = 'none';
    loginScreen.style.display = 'block';
    pinInput.value = '';
    pinInput.focus();
}
