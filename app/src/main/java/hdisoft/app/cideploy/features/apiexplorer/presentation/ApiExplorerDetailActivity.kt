package hdisoft.app.cideploy.features.apiexplorer.presentation

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import hdisoft.app.cideploy.R
import hdisoft.app.cideploy.features.apiexplorer.data.ApiCatalog
import hdisoft.app.cideploy.features.apiexplorer.data.ApiClient
import hdisoft.app.cideploy.features.apiexplorer.data.ApiEndpoint
import hdisoft.app.cideploy.features.apiexplorer.data.ApiResult
import hdisoft.app.cideploy.features.apiexplorer.data.ApiSessionPrefs
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ApiExplorerDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENDPOINT_ID = "endpoint_id"
    }

    private lateinit var sessionPrefs: ApiSessionPrefs
    private lateinit var endpoint: ApiEndpoint
    private val paramFields = mutableMapOf<String, EditText>()

    private lateinit var llParams: android.widget.LinearLayout
    private lateinit var tvBodyLabel: TextView
    private lateinit var etBody: EditText
    private lateinit var btnExecute: android.widget.Button
    private lateinit var pbExecuting: android.widget.ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvResponse: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_detail)

        val endpointId = intent.getStringExtra(EXTRA_ENDPOINT_ID)
        val resolvedEndpoint = ApiCatalog.find(endpointId ?: "")
        if (resolvedEndpoint == null) {
            finish()
            return
        }
        endpoint = resolvedEndpoint
        sessionPrefs = ApiSessionPrefs(this)

        val toolbar: Toolbar = findViewById(R.id.toolbarApiDetail)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.title = endpoint.title
        toolbar.setNavigationOnClickListener { finish() }

        val tvHeader: TextView = findViewById(R.id.tvHeader)
        tvHeader.text = "${endpoint.method} ${endpoint.pathTemplate}"

        llParams = findViewById(R.id.llParams)
        tvBodyLabel = findViewById(R.id.tvBodyLabel)
        etBody = findViewById(R.id.etBody)
        btnExecute = findViewById(R.id.btnExecute)
        pbExecuting = findViewById(R.id.pbExecuting)
        tvStatus = findViewById(R.id.tvStatus)
        tvResponse = findViewById(R.id.tvResponse)

        buildParamFields()

        if (endpoint.hasBody) {
            tvBodyLabel.visibility = View.VISIBLE
            etBody.visibility = View.VISIBLE
            etBody.setText(endpoint.sampleBody ?: "{}")
        }

        btnExecute.setOnClickListener { execute() }
    }

    private fun buildParamFields() {
        for (paramName in endpoint.paramNames()) {
            val label = TextView(this).apply {
                text = paramName
                textSize = 13f
                setPadding(0, 16, 0, 4)
            }
            val field = EditText(this).apply {
                hint = paramName
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
            }
            llParams.addView(label)
            llParams.addView(field)
            paramFields[paramName] = field
        }
    }

    private fun execute() {
        val host = sessionPrefs.getHost()
        if (host.isEmpty()) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Set a host first (gear icon on the previous screen)"
            tvStatus.setTextColor(Color.parseColor("#FF5252"))
            return
        }

        val paramValues = paramFields.mapValues { it.value.text.toString().trim() }
        val missing = paramValues.filterValues { it.isEmpty() }.keys
        if (missing.isNotEmpty()) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Missing value for: ${missing.joinToString(", ")}"
            tvStatus.setTextColor(Color.parseColor("#FF5252"))
            return
        }

        val resolvedPath = endpoint.resolvePath(paramValues)
        val url = "http://$host:${sessionPrefs.getPort()}$resolvedPath"
        val body = if (endpoint.hasBody) etBody.text.toString() else null

        btnExecute.isEnabled = false
        pbExecuting.visibility = View.VISIBLE
        tvStatus.visibility = View.GONE
        tvResponse.text = ""

        lifecycleScope.launch {
            val result = ApiClient.execute(
                method = endpoint.method,
                urlString = url,
                username = sessionPrefs.getUsername(),
                password = sessionPrefs.getPassword(),
                body = body
            )
            showResult(result)
        }
    }

    private fun showResult(result: ApiResult) {
        btnExecute.isEnabled = true
        pbExecuting.visibility = View.GONE
        tvStatus.visibility = View.VISIBLE

        if (result.error != null) {
            tvStatus.text = "Request failed"
            tvStatus.setTextColor(Color.parseColor("#FF5252"))
            tvResponse.text = result.error
            return
        }

        tvStatus.text = "HTTP ${result.statusCode}"
        tvStatus.setTextColor(if (result.isSuccess) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252"))
        tvResponse.text = prettyPrint(result.body)
    }

    private fun prettyPrint(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "(empty response)"
        return try {
            JSONObject(trimmed).toString(2)
        } catch (e: Exception) {
            try {
                JSONArray(trimmed).toString(2)
            } catch (e2: Exception) {
                raw
            }
        }
    }
}
