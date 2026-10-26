package com.annotatio.maximus.util

import android.content.Context
import android.net.Uri
import com.annotatio.maximus.model.DrawingScale
import com.annotatio.maximus.model.MeasurementItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

object AndroidMcpServer {

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    var isRunning: Boolean = false
        private set
    var port: Int = 8765
        private set

    // Provider callbacks to connect to active PDF state
    var documentInfoProvider: (() -> JSONObject)? = null
    var pageTextProvider: ((Int) -> String)? = null
    var searchProvider: ((String) -> JSONArray)? = null
    var takeoffProvider: (() -> List<MeasurementItem>)? = null
    var addNoteCallback: ((Int, Float, Float, String) -> Boolean)? = null
    var setScaleCallback: ((String) -> Boolean)? = null

    fun start(scope: CoroutineScope, preferredPort: Int = 8765): Int {
        if (isRunning) return port
        try {
            serverSocket = ServerSocket(preferredPort)
            port = serverSocket!!.localPort
            isRunning = true

            job = scope.launch(Dispatchers.IO) {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to random free port
            try {
                serverSocket = ServerSocket(0)
                port = serverSocket!!.localPort
                isRunning = true
                job = scope.launch(Dispatchers.IO) {
                    while (isRunning) {
                        try {
                            val client = serverSocket?.accept() ?: break
                            handleClient(client)
                        } catch (_: Exception) {
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                isRunning = false
            }
        }
        return port
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        job?.cancel()
        job = null
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // Read HTTP request line
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                if (line!!.lowercase().startsWith("content-length:")) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(chars, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                String(chars, 0, readTotal)
            } else ""

            val responseJson = if (body.isNotBlank()) {
                handleJsonRpc(body)
            } else {
                JSONObject().apply {
                    put("status", "AnnotatioMaximus MCP Server running")
                    put("version", "1.0")
                }.toString()
            }

            val responseBytes = responseJson.toByteArray(Charsets.UTF_8)
            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: application/json; charset=UTF-8\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Access-Control-Allow-Headers: *\r\n")
            writer.print("Content-Length: ${responseBytes.size}\r\n")
            writer.print("\r\n")
            writer.flush()

            socket.getOutputStream().write(responseBytes)
            socket.getOutputStream().flush()
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleJsonRpc(body: String): String {
        return try {
            val req = JSONObject(body)
            val id = req.opt("id") ?: 1
            val method = req.optString("method")
            val params = req.optJSONObject("params") ?: JSONObject()

            val result = JSONObject()
            when (method) {
                "initialize" -> {
                    result.put("protocolVersion", "2024-11-05")
                    result.put("capabilities", JSONObject().apply {
                        put("tools", JSONObject().apply { put("listChanged", false) })
                    })
                    result.put("serverInfo", JSONObject().apply {
                        put("name", "annotatio-maximus-mcp")
                        put("version", "1.0.0")
                    })
                }
                "tools/list" -> {
                    val tools = JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "get_document_info")
                            put("description", "Gibt Informationen über das aktuell geöffnete PDF zurück (Titel, Seitenzahl).")
                            put("inputSchema", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject())
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "get_page_text")
                            put("description", "Extrahiert den Text einer angegebenen Seite (0-indiziert).")
                            put("inputSchema", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("pageIndex", JSONObject().apply { put("type", "integer") })
                                })
                                put("required", JSONArray().apply { put("pageIndex") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "search_document")
                            put("description", "Sucht nach Textbegriffen im Dokument.")
                            put("inputSchema", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("query", JSONObject().apply { put("type", "string") })
                                })
                                put("required", JSONArray().apply { put("query") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "get_takeoff_summary")
                            put("description", "Gibt alle Messungen und Mengen (Strecken, Flächen, Stückzahlen) der Mengenermittlung zurück.")
                            put("inputSchema", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject())
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "add_text_note")
                            put("description", "Fügt eine Textnotiz an normalisierten Koordinaten (x, y: 0..1) hinzu.")
                            put("inputSchema", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("pageIndex", JSONObject().apply { put("type", "integer") })
                                    put("x", JSONObject().apply { put("type", "number") })
                                    put("y", JSONObject().apply { put("type", "number") })
                                    put("text", JSONObject().apply { put("type", "string") })
                                })
                                put("required", JSONArray().apply { put("pageIndex"); put("x"); put("y"); put("text") })
                            })
                        })
                    }
                    result.put("tools", tools)
                }
                "tools/call" -> {
                    val toolName = params.optString("name")
                    val arguments = params.optJSONObject("arguments") ?: JSONObject()
                    val content = JSONArray()

                    val textResult = when (toolName) {
                        "get_document_info" -> {
                            val info = documentInfoProvider?.invoke() ?: JSONObject().apply {
                                put("status", "Kein Dokument geöffnet")
                            }
                            info.toString(2)
                        }
                        "get_page_text" -> {
                            val pageIdx = arguments.optInt("pageIndex", 0)
                            pageTextProvider?.invoke(pageIdx) ?: "Kein Text verfügbar"
                        }
                        "search_document" -> {
                            val query = arguments.optString("query")
                            val hits = searchProvider?.invoke(query) ?: JSONArray()
                            hits.toString(2)
                        }
                        "get_takeoff_summary" -> {
                            val items = takeoffProvider?.invoke() ?: emptyList()
                            MeasureCalculator.exportTakeoffToCsv(items)
                        }
                        "add_text_note" -> {
                            val pageIdx = arguments.optInt("pageIndex", 0)
                            val x = arguments.optDouble("x", 0.5).toFloat()
                            val y = arguments.optDouble("y", 0.5).toFloat()
                            val text = arguments.optString("text")
                            val success = addNoteCallback?.invoke(pageIdx, x, y, text) ?: false
                            if (success) "Notiz erfolgreich platziert." else "Konnte Notiz nicht hinzufügen."
                        }
                        else -> "Unbekanntes Tool: $toolName"
                    }

                    content.put(JSONObject().apply {
                        put("type", "text")
                        put("text", textResult)
                    })
                    result.put("content", content)
                }
                else -> {
                    result.put("error", "Method not supported: $method")
                }
            }

            JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", result)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("jsonrpc", "2.0")
                put("error", JSONObject().apply {
                    put("code", -32603)
                    put("message", e.localizedMessage ?: "Internal error")
                })
            }.toString()
        }
    }
}
