const { createApp, ref, reactive, onMounted } = Vue;

createApp({
    setup() {
        // Current view state
        const currentView = ref('home');
        const pageTitle = ref('QA Automation Control Center');
        const iframeSrc = ref('');
        const iframeVisible = ref(false);

        // Sidebar Navigation
        function switchView(view) {
            currentView.value = view;
            iframeVisible.value = false;
            iframeSrc.value = '';

            if (view === 'home') {
                pageTitle.value = 'QA Automation Control Center';
            } else if (view === 'script') {
                pageTitle.value = 'Script Console';
                loadScripts();
            } else if (view === 'test') {
                pageTitle.value = 'Test Console';
                openAppStatus.text = '';
            } else if (view === 'reports') {
                pageTitle.value = 'Test Reports';
                iframeSrc.value = '/reports';
                iframeVisible.value = true;
            }
        }

        // --- Script Library CRUD States & Methods ---
        const scriptSubView = ref('list'); // 'list' or 'detail'
        const scriptsList = ref([]);
        const loadingScripts = ref(false);
        const scriptStatus = reactive({
            text: '',
            isError: false
        });

        const editingScript = reactive({
            id: null,
            name: '',
            content: '',
            runCount: 0,
            lastRun: '',
            lastUpdate: ''
        });

        function loadScripts() {
            loadingScripts.value = true;
            scriptStatus.text = '';
            fetch('/api/qa/scripts')
                .then(res => {
                    if (!res.ok) throw new Error('Failed to load scripts from database.');
                    return res.json();
                })
                .then(data => {
                    scriptsList.value = data;
                })
                .catch(err => {
                    setScriptStatus(err.message, true);
                })
                .finally(() => {
                    loadingScripts.value = false;
                });
        }

        function setScriptStatus(msg, isErr = false) {
            scriptStatus.text = msg;
            scriptStatus.isError = isErr;
        }

        function openNewScriptForm() {
            editingScript.id = null;
            editingScript.name = '';
            editingScript.content = '';
            editingScript.runCount = 0;
            editingScript.lastRun = '';
            editingScript.lastUpdate = '';
            setScriptStatus('');
            scriptSubView.value = 'detail';
        }

        function editScript(script) {
            editingScript.id = script.id;
            editingScript.name = script.name;
            editingScript.content = script.content;
            editingScript.runCount = script.runCount;
            editingScript.lastRun = script.lastRun;
            editingScript.lastUpdate = script.lastUpdate;
            setScriptStatus('');
            scriptSubView.value = 'detail';
        }

        function saveScript() {
            if (!editingScript.name.trim()) {
                setScriptStatus('Error: Script name cannot be empty.', true);
                return;
            }
            if (!editingScript.content.trim()) {
                setScriptStatus('Error: Script content cannot be empty.', true);
                return;
            }

            // Validate JSON array format
            try {
                const parsed = JSON.parse(editingScript.content);
                if (!Array.isArray(parsed)) {
                    throw new Error('Script content must be a JSON array representing steps.');
                }
            } catch (e) {
                setScriptStatus('Error: Invalid JSON array format. ' + e.message, true);
                return;
            }

            setScriptStatus('Saving script details...');
            const isEditing = editingScript.id !== null;
            const method = isEditing ? 'PUT' : 'POST';
            const url = isEditing ? `/api/qa/scripts?id=${editingScript.id}` : '/api/qa/scripts';

            fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: editingScript.name,
                    content: editingScript.content
                })
            })
                .then(res => {
                    if (!res.ok) return res.json().then(err => { throw new Error(err.message || 'Save operation failed'); });
                    return res.json();
                })
                .then(data => {
                    setScriptStatus('✓ Script saved successfully!');
                    // Transition back to list after a brief delay
                    setTimeout(() => {
                        scriptSubView.value = 'list';
                        loadScripts();
                    }, 800);
                })
                .catch(err => {
                    setScriptStatus('❌ Failed to save script: ' + err.message, true);
                });
        }

        function deleteScript(id) {
            if (!confirm('Are you sure you want to delete this script scenario?')) return;

            setScriptStatus('Deleting script...');
            fetch(`/api/qa/scripts?id=${id}`, {
                method: 'DELETE'
            })
                .then(res => {
                    if (!res.ok) return res.json().then(err => { throw new Error(err.message || 'Delete operation failed'); });
                    return res.json();
                })
                .then(() => {
                    setScriptStatus('✓ Script deleted successfully!');
                    loadScripts();
                    // If we deleted the script we were viewing, go back to list
                    if (editingScript.id === id) {
                        scriptSubView.value = 'list';
                    }
                })
                .catch(err => {
                    setScriptStatus('❌ Failed to delete script: ' + err.message, true);
                });
        }

        function runScript(id) {
            setScriptStatus('Initiating script execution...');
            fetch('/api/qa/runscript', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: parseInt(id) })
            })
                .then(res => {
                    if (!res.ok) return res.json().then(err => { throw new Error(err.message || 'Execution failed'); });
                    return res.json();
                })
                .then(data => {
                    setScriptStatus('✓ Script execution started on device!');
                    // If we're on detail view, update the run stats on page
                    if (editingScript.id === id) {
                        editingScript.runCount++;
                        editingScript.lastRun = new Date().toISOString();
                    }
                    loadScripts();
                })
                .catch(err => {
                    setScriptStatus('❌ Failed to execute script: ' + err.message, true);
                });
        }

        // Run instantly directly from the detail raw text editor
        function runScriptInstantly() {
            if (!editingScript.content.trim()) {
                setScriptStatus('Error: Content is empty.', true);
                return;
            }
            try {
                JSON.parse(editingScript.content);
            } catch (e) {
                setScriptStatus('Error: Invalid JSON structure: ' + e.message, true);
                return;
            }

            setScriptStatus('Executing inline script...');
            fetch('/api/qa/runscript', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: editingScript.content
            })
                .then(res => {
                    if (!res.ok) return res.json().then(err => { throw new Error(err.message || 'Execution failed'); });
                    return res.json();
                })
                .then(() => {
                    setScriptStatus('✓ Instant script execution started!');
                })
                .catch(err => {
                    setScriptStatus('❌ Instant run failed: ' + err.message, true);
                });
        }

        // --- Test Console (Device Actions) States & Methods ---
        const openAppQuery = ref('');
        const coordinateX = ref('');
        const coordinateY = ref('');
        const screenAction = ref('none');
        const openAppStatus = reactive({
            text: '',
            isError: false
        });

        function setOpenAppStatus(msg, isErr = false) {
            openAppStatus.text = msg;
            openAppStatus.isError = isErr;
        }

        function runCustomScript() {
            const scriptSteps = [];
            
            // 1. Open App step
            const app = openAppQuery.value.trim();
            if (app) {
                scriptSteps.push({ action: 'open_app', query: app });
            }
            
            // 2. Tap step
            const x = coordinateX.value.toString().trim();
            const y = coordinateY.value.toString().trim();
            if (x !== '' && y !== '') {
                scriptSteps.push({
                    action: 'tap',
                    x: parseFloat(x),
                    y: parseFloat(y)
                });
            }
            
            // 3. Screen actions
            if (screenAction.value === 'capture') {
                scriptSteps.push({ action: 'capture' });
            } else if (screenAction.value === 'record') {
                scriptSteps.push({ action: 'record', duration: 5000 });
            }
            
            if (scriptSteps.length === 0) {
                setOpenAppStatus('Error: Please fill or select at least one action.', true);
                return;
            }
            
            setOpenAppStatus('Starting execution of custom script...');
            fetch('/api/qa/runscript', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(scriptSteps)
            })
                .then(res => {
                    if (!res.ok) return res.json().then(err => { throw new Error(err.message || 'Execution error'); });
                    return res.json();
                })
                .then(data => {
                    setOpenAppStatus('✓ Script execution started: ' + data.message);
                })
                .catch(err => {
                    setOpenAppStatus('❌ Custom script failed: ' + err.message, true);
                });
        }

        // Open App Only
        function openAppOnly() {
            const query = openAppQuery.value.trim();
            if (!query) {
                setOpenAppStatus('Error: Please enter an app name or package name.', true);
                return;
            }

            setOpenAppStatus('Launching app...');
            fetch('/api/qa/openapp', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ query: query })
            })
                .then(res => {
                    if (!res.ok) return res.json().then(err => { throw new Error(err.message || 'App not found'); });
                    return res.json();
                })
                .then(() => {
                    setOpenAppStatus('✓ App launched successfully!');
                })
                .catch(err => {
                    setOpenAppStatus('❌ Failed to open app: ' + err.message, true);
                });
        }

        // --- Helper Formatting Utilities ---
        function formatTime(isoString) {
            if (!isoString) return 'Never';
            try {
                return new Date(isoString).toLocaleString();
            } catch (e) {
                return isoString;
            }
        }

        return {
            // General view states
            currentView,
            pageTitle,
            iframeSrc,
            iframeVisible,
            switchView,

            // Script library states & functions
            scriptSubView,
            scriptsList,
            loadingScripts,
            scriptStatus,
            editingScript,
            loadScripts,
            openNewScriptForm,
            editScript,
            saveScript,
            deleteScript,
            runScript,
            runScriptInstantly,

            // Test console states & functions
            openAppQuery,
            coordinateX,
            coordinateY,
            screenAction,
            openAppStatus,
            runCustomScript,
            openAppOnly,

            // Utilities
            formatTime
        };
    }
}).mount('#app');
