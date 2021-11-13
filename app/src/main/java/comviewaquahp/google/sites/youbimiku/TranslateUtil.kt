package comviewaquahp.google.sites.youbimiku

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class TranslateUtil {
    companion object {
        private const val endpoint = "https://script.google.com/macros/s/AKfycbyvt1mZ72ixUDLTQyWJAKk0XFzwG-Ne3CyXBTac0tvTPJmBQ_ldwptXQptyZwwWxEjQPw/exec"
        fun translateJpToEn(text: String): String {
            try {
                val url = URL("$endpoint?text=$text")
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.connect()

                val sb = StringBuilder()
                val br = BufferedReader(InputStreamReader(urlConnection.inputStream))
                br.readLines().forEach {
                    sb.append(it)
                }
                br.close()
                return sb.toString()
            } catch (ex: Exception) {
                println("failure:$ex")
                return String.toString()
            }
        }
    }
}