const { createApp, ref, reactive, computed, watch } = Vue;

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
            } else if (view === 'actions') {
                pageTitle.value = 'Action List';
                loadActionDefinitions();
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

        // Loaded from ActionRegistry through GET /api/qa/steps. Adding a core
        // action to the registry automatically adds it to this Web Action List.
        const actionDefinitions = ref([]);
        const actionDefinitionsLoading = ref(false);
        const actionSearch = ref('');
        const actionCategory = ref('all');
        const actionStatus = reactive({ text: '', isError: false });
        const selectedActionName = ref('');
        const actionInputText = ref('null');
        const actionOutputText = ref('');
        const actionOutputImageUrl = ref('');
        const actionOutputVideoUrl = ref('');
        const actionResultState = ref('');
        const actionRunning = ref(false);
        const filteredActionDefinitions = computed(() => {
            const query = actionSearch.value.trim().toLowerCase();
            return actionDefinitions.value.filter(action => {
                const matchesCategory = actionCategory.value === 'all' || action.category === actionCategory.value;
                const searchable = `${action.name} ${action.displayName} ${action.description} ${action.className}`.toLowerCase();
                return matchesCategory && (!query || searchable.includes(query));
            });
        });
        const selectedAction = computed(() =>
            actionDefinitions.value.find(action => action.name === selectedActionName.value) || null
        );

        async function loadActionDefinitions() {
            if (actionDefinitionsLoading.value) return;
            actionDefinitionsLoading.value = true;
            try {
                const response = await fetch('/api/qa/steps');
                if (!response.ok) throw new Error(`Failed to load steps (${response.status})`);
                const definitions = await response.json();
                if (!Array.isArray(definitions)) throw new Error('Invalid step definition response');
                actionDefinitions.value = definitions;
                if (definitions.length && !selectedAction.value) selectAction(definitions[0]);
            } catch (error) {
                setActionStatus(error.message, true);
            } finally {
                actionDefinitionsLoading.value = false;
            }
        }

        watch(filteredActionDefinitions, actions => {
            if (actions.length && !actions.some(action => action.name === selectedActionName.value)) {
                selectAction(actions[0]);
            }
        });

        function prettyJson(value) {
            return JSON.stringify(value, null, 2);
        }

        function setActionStatus(message, isError = false) {
            actionStatus.text = message;
            actionStatus.isError = isError;
            window.setTimeout(() => {
                if (actionStatus.text === message) actionStatus.text = '';
            }, 2200);
        }

        function selectAction(action) {
            selectedActionName.value = action.name;
            actionInputText.value = prettyJson(action.input);
            actionOutputText.value = '';
            actionOutputImageUrl.value = '';
            actionOutputVideoUrl.value = '';
            actionResultState.value = '';
        }

        function resetActionInput() {
            if (!selectedAction.value) return;
            actionInputText.value = prettyJson(selectedAction.value.input);
            actionOutputText.value = '';
            actionOutputImageUrl.value = '';
            actionOutputVideoUrl.value = '';
            actionResultState.value = '';
        }

        async function runSelectedAction() {
            const action = selectedAction.value;
            if (!action || actionRunning.value) return;

            let input;
            try {
                input = JSON.parse(actionInputText.value.trim() || 'null');
            } catch (error) {
                actionResultState.value = 'error';
                actionOutputText.value = `Invalid input JSON: ${error.message}`;
                return;
            }

            const executableAction = {
                ...JSON.parse(JSON.stringify(action.example)),
                input,
                output: null
            };

            actionRunning.value = true;
            actionResultState.value = '';
            actionOutputImageUrl.value = '';
            actionOutputVideoUrl.value = '';
            actionOutputText.value = 'Running action on device…';

            try {
                const response = await fetch('/api/qa/runscript', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify([executableAction])
                });
                const rawResult = await response.text();
                let result;
                try {
                    result = JSON.parse(rawResult);
                } catch (error) {
                    throw new Error(rawResult || `Invalid server response (${response.status})`);
                }
                if (!response.ok || result.status === 'error') {
                    throw new Error(result.message || `Execution failed (${response.status})`);
                }
                actionOutputText.value = prettyJson(result);
                const output = result && result.action ? result.action.output : null;
                if (action.name === 'capture' && typeof output === 'string' && output) {
                    actionOutputImageUrl.value = output;
                }
                if (action.name === 'record' && typeof output === 'string' && output) {
                    actionOutputVideoUrl.value = output;
                }
                actionResultState.value = 'success';
            } catch (error) {
                actionOutputText.value = prettyJson({
                    status: 'error',
                    message: error.message
                });
                actionResultState.value = 'error';
            } finally {
                actionRunning.value = false;
            }
        }

        async function selectAndRunAction(name) {
            const action = actionDefinitions.value.find(item => item.name === name);
            if (!action || actionRunning.value) return;
            selectAction(action);
            await runSelectedAction();
        }

        async function copyActionJson(action) {
            const json = prettyJson(action.example);
            try {
                if (navigator.clipboard && window.isSecureContext) {
                    await navigator.clipboard.writeText(json);
                } else {
                    const textarea = document.createElement('textarea');
                    textarea.value = json;
                    textarea.style.position = 'fixed';
                    textarea.style.opacity = '0';
                    document.body.appendChild(textarea);
                    textarea.select();
                    const copied = document.execCommand('copy');
                    textarea.remove();
                    if (!copied) throw new Error('Copy command failed');
                }
                setActionStatus(`${action.displayName} JSON copied.`);
            } catch (error) {
                setActionStatus('Clipboard access is unavailable in this browser.', true);
            }
        }

        function useActionInScript(action) {
            let actions = [];
            if (editingScript.content.trim()) {
                try {
                    actions = parseAndValidateActions(editingScript.content);
                } catch (error) {
                    setActionStatus('Current editor content is not a valid action array.', true);
                    return;
                }
            }
            actions.push(JSON.parse(JSON.stringify(action.example)));
            editingScript.content = prettyJson(actions);
            if (!editingScript.name) editingScript.name = 'New Action Script';
            scriptSubView.value = 'detail';
            switchView('script');
            setScriptStatus(`Added ${action.displayName} to the script.`);
        }

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

        function parseAndValidateActions(content) {
            const actions = JSON.parse(content);
            if (!Array.isArray(actions)) {
                throw new Error('Script content must be a JSON array representing actions.');
            }
            actions.forEach((action, index) => {
                if (!action || typeof action !== 'object' || Array.isArray(action)) {
                    throw new Error(`Action at index ${index} must be a JSON object.`);
                }
                // `action` is accepted only as a migration alias for saved
                // legacy scripts. ActionModel validation itself only needs name.
                const name = typeof action.name === 'string' ? action.name : action.action;
                if (typeof name !== 'string' || !name.trim()) {
                    throw new Error(`Action at index ${index} must have a non-empty name.`);
                }
            });
            return actions;
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
                parseAndValidateActions(editingScript.content);
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
                parseAndValidateActions(editingScript.content);
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
                scriptSteps.push({ name: 'open_app', category: 'core', input: { query: app } });
            }
            
            // 2. Tap step
            const x = coordinateX.value.toString().trim();
            const y = coordinateY.value.toString().trim();
            if (x !== '' && y !== '') {
                scriptSteps.push({
                    name: 'tap',
                    category: 'core',
                    input: {
                        x: parseFloat(x),
                        y: parseFloat(y)
                    }
                });
            }
            
            // 3. Screen actions
            if (screenAction.value === 'capture') {
                scriptSteps.push({ name: 'capture', category: 'core' });
            } else if (screenAction.value === 'record') {
                scriptSteps.push({ name: 'record', category: 'core', input: { duration: 5000 } });
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

            // Action definitions
            actionDefinitions,
            actionDefinitionsLoading,
            filteredActionDefinitions,
            actionSearch,
            actionCategory,
            actionStatus,
            selectedAction,
            actionInputText,
            actionOutputText,
            actionOutputImageUrl,
            actionOutputVideoUrl,
            actionResultState,
            actionRunning,
            loadActionDefinitions,
            prettyJson,
            selectAction,
            resetActionInput,
            runSelectedAction,
            selectAndRunAction,
            copyActionJson,
            useActionInScript,

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
