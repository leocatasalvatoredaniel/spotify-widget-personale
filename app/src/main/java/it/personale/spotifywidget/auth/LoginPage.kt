package it.personale.spotifywidget.auth

/**
 * Page shown by the browser at the end of the login, with the way back to
 * the app (spotifywidget://callback, handled by AuthCallbackActivity)
 */
fun loginPage(title: String, message: String?, button: String, success: Boolean): String {
    val back = "spotifywidget://callback?status=${if (success) "ok" else "error"}"
    fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
    return """
        <!DOCTYPE html>
        <html lang="it">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${escape(title)}</title>
        <style>
          body { font-family: sans-serif; margin: 0; min-height: 100vh; display: flex;
                 align-items: center; justify-content: center; background: #121212; color: #fff; }
          main { max-width: 22rem; padding: 1.5rem; text-align: center; }
          h1 { font-size: 1.5rem; }
          p { color: #d9d9d9; line-height: 1.5; }
          a { display: inline-block; margin-top: 1rem; padding: .9rem 1.5rem; border-radius: 2rem;
              background: #fff; color: #121212; font-weight: bold; text-decoration: none; }
        </style>
        </head>
        <body>
        <main>
          <h1>${escape(title)}</h1>
          ${message?.let { "<p>${escape(it)}</p>" }.orEmpty()}
          <a href="$back">${escape(button)}</a>
        </main>
        </body>
        </html>
    """.trimIndent()
}
