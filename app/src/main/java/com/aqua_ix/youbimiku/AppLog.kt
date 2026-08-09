package com.aqua_ix.youbimiku

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.PrintWriter
import java.io.StringWriter
import java.net.MalformedURLException
import java.net.URL
import java.util.Collections
import java.util.IdentityHashMap

/**
 * ログ出力とCrashlyticsへの記録をまとめる薄いラッパー。
 *
 * アプリのコードは[Log]を直接呼ばず、ここを通す。理由は3つ。
 *
 * 1. **本文を出さない。** 会話の本文はユーザーの個人的な内容を含みうる。端末のログは
 *    同一端末上の他アプリからは読めないが、USBデバッグやバグレポート経由では取り出せる。
 *    呼び出し側は本文ではなく文字数や種別だけを渡し、ここでも念のため伏せる。
 * 2. **シークレットを出さない。** `secrets.properties` 由来の通信先URLは、意図した
 *    ログ出力だけでなく例外のメッセージにも現れる（Ktorのタイムアウト例外は
 *    `Request timeout has expired [url=...]` の形でURLを埋め込む）。呼び出し側で
 *    気を付けるのは漏れやすいため、出力とCrashlyticsへの記録の直前に[redact]で伏せる。
 * 3. **リリースビルドで黙る。** デバッグ用の[d]は`BuildConfig.DEBUG`でなければ何もしない。
 *    R8を有効にすれば（#24）この分岐ごと消える。
 *
 * [e]に例外を渡すと、Crashlyticsに致命的でない例外（non-fatal）として記録する。
 * 送るのは例外の型・スタックトレース・ここで組み立てたメッセージだけで、
 * 会話の本文やユーザーの識別子は送らない。
 */
object AppLog {

    /** 例外を辿る深さの上限。cause・suppressedが深く積み重なっていても止まるようにする */
    private const val MAX_CAUSE_DEPTH = 10

    /**
     * ログとCrashlyticsから伏せる値。
     * `secrets.properties` の値は`BuildConfig`に埋め込まれ、例外のメッセージや
     * URLのログにそのまま現れるため、通信先を特定できる項目をまとめて対象にする。
     */
    private val secretPatterns: List<Regex> by lazy {
        buildSecretPatterns(
            listOf(
                BuildConfig.TRANSLATE_END_POINT,
                BuildConfig.REPORT_END_POINT,
                BuildConfig.AVATAR_BASE_URL,
                BuildConfig.DIALOGFLOW_PROJECT_ID,
            )
        )
    }

    /**
     * デバッグビルドでのみ出力する。リリースビルドでは何もしない。
     *
     * メッセージは遅延評価で受け取る。文字列にする側で値を読むもの（RemoteConfigの
     * 各getterなど）があるため、捨てるだけのメッセージを組み立てるコストを
     * リリースビルドで払わないようにする。
     */
    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            debug(tag, message())
        }
    }

    /** [d]から呼ぶための実体。[redact]がprivateなのでinline関数からは直接呼べない */
    @PublishedApi
    internal fun debug(tag: String, message: String) {
        Log.d(tag, redact(message))
    }

    fun w(tag: String, message: String) {
        Log.w(tag, redact(message))
    }

    /** 記録するほどではない失敗。ログに残すだけでCrashlyticsには送らない */
    fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, describe(message, throwable))
    }

    fun e(tag: String, message: String) {
        Log.e(tag, redact(message))
        breadcrumb(message)
    }

    /**
     * 失敗をログに残し、Crashlyticsに致命的でない例外として記録する。
     *
     * [throwable]がnullの場合は記録できるものが無いのでログだけに留める
     * （`Task.getException()`のように、失敗しても例外が無い場合がある）。
     */
    fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, describe(message, throwable))
        breadcrumb(message)
        throwable?.let { crashlytics()?.recordException(sanitize(it)) }
    }

    /**
     * クラッシュレポートに添える情報を設定する。
     * 集計や再現に使える種別・件数だけを渡し、本文や識別子は渡さない。
     */
    fun setCustomKey(key: String, value: String) {
        crashlytics()?.setCustomKey(key, redact(value))
    }

    fun setCustomKey(key: String, value: Int) {
        crashlytics()?.setCustomKey(key, value)
    }

    /**
     * クラッシュレポートに残す足跡。
     * クラッシュした時点までに何が起きていたかを追えるようにする。
     */
    private fun breadcrumb(message: String) {
        crashlytics()?.log(redact(message))
    }

    /**
     * Firebaseの初期化前に呼ばれた場合は[FirebaseCrashlytics.getInstance]が失敗するため、
     * ログ出力そのものを妨げないようnullを返す（結果はキャッシュしない）。
     */
    private fun crashlytics(): FirebaseCrashlytics? = try {
        FirebaseCrashlytics.getInstance()
    } catch (e: IllegalStateException) {
        null
    }

    private fun describe(message: String, throwable: Throwable?): String =
        if (throwable == null) {
            redact(message)
        } else {
            "${redact(message)}\n${redact(stackTraceOf(throwable))}"
        }

    /**
     * スタックトレースを文字列にする。
     * [Log.getStackTraceString]は`UnknownHostException`を含む例外に空文字を返すため使わない
     * （圏外や名前解決の失敗で、失敗したことしか分からなくなる）。
     */
    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString()
    }

    /** シークレットと、それに続くパス・クエリを伏せる */
    private fun redact(message: String): String = redactSecrets(message, secretPatterns)

    /**
     * Crashlyticsに送れる形の例外を返す。
     *
     * [FirebaseCrashlytics.recordException]は例外のメッセージもそのまま送るため、
     * シークレットを含む場合だけ伏せた写しに差し替える。含まない場合は元の例外を返し、
     * Crashlytics側で本来の型でまとめられるようにする。
     */
    private fun sanitize(throwable: Throwable): Throwable =
        if (containsSecret(throwable)) redactedCopy(throwable, 0) else throwable

    /**
     * 例外のどこかにシークレットが含まれているかを返す。
     *
     * causeの連鎖だけでなくsuppressedとその先も辿る。Crashlyticsが送るのはcauseの
     * 連鎖だけだが、判定を送信側の実装に依存させず、「例外のどこかにあれば伏せる」で
     * 揃えておく。一度見た例外は二度辿らないので、causeやsuppressedが循環していても止まる。
     */
    private fun containsSecret(throwable: Throwable): Boolean {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val pending = ArrayDeque<Pair<Throwable, Int>>()
        pending.addLast(throwable to 0)
        while (pending.isNotEmpty()) {
            val (current, depth) = pending.removeLast()
            if (!seen.add(current)) {
                continue
            }
            val message = current.message
            if (message != null && redact(message) != message) {
                return true
            }
            if (depth >= MAX_CAUSE_DEPTH) {
                continue
            }
            current.cause?.let { pending.addLast(it to depth + 1) }
            current.suppressed.forEach { pending.addLast(it to depth + 1) }
        }
        return false
    }

    /**
     * メッセージを伏せた例外の写しを作る。
     * 型が[RedactedException]に変わってしまうため、元の型はメッセージの先頭に残す。
     */
    private fun redactedCopy(throwable: Throwable, depth: Int): Throwable {
        val cause = throwable.cause
            ?.takeIf { it !== throwable && depth < MAX_CAUSE_DEPTH }
            ?.let { redactedCopy(it, depth + 1) }
        return RedactedException(
            "${throwable.javaClass.name}: ${redact(throwable.message.orEmpty())}",
            cause,
        ).apply { stackTrace = throwable.stackTrace }
    }
}

/** シークレットを取り除いた例外。元の型はメッセージの先頭に残る */
class RedactedException(message: String, cause: Throwable?) : Exception(message, cause)

/** 伏せた値の代わりに出す文字列 */
internal const val MASK = "***"

/**
 * 伏せる対象として扱う値の最短の長さ。
 * 空文字や1文字から作ったパターンはあらゆる位置にあたり、行全体が[MASK]になってしまうため、
 * 短すぎる値（未設定のシークレットなど）は対象にしない。
 */
private const val MIN_SECRET_LENGTH = 8

/**
 * シークレットに続けて伏せる範囲。URLとして続きうる文字を、区切りに見える文字の手前まで。
 *
 * ホストだけを伏せても足りない。翻訳APIは本文をクエリに載せるため、
 * Ktorのタイムアウト例外は`url=<エンドポイント>?text=<ユーザーの発言>&target=ja`の形になり、
 * エンドポイントだけを置き換えるとユーザーの発言がそのまま残ってしまう。
 * 区切りの手前で止めることで、例外の残り（タイムアウト値など）は読める形で残す。
 */
private const val URL_TAIL_PATTERN = """[^\s,)\]}'"]*"""

/**
 * OpenAIのAPIキー。Firebase経由で受け取るため`BuildConfig`には無く固定値で持てないが、
 * 万一ログや例外に現れても値が残らないようにする。
 */
private val API_KEY_PATTERN = Regex("sk-[A-Za-z0-9_-]{8,}")

/**
 * ホスト名だけを伏せるときに、その手前で切らないための境界。
 *
 * ホスト名の一部として続いている場合（`notavatar.example.com`）は別の通信先なので伏せない。
 * 境界を見ずに部分一致で伏せると、関係のない通信先のログまで読めなくなる。
 */
private const val HOST_BOUNDARY_PATTERN = """(?<![A-Za-z0-9.\-])"""

/**
 * 伏せる値から、続くパス・クエリまで含めて拾うパターンを作る。
 * 短すぎる値は[MIN_SECRET_LENGTH]で落とす。
 *
 * URLのシークレットは、スキームを含む値そのものに加えて**ホスト名だけのパターン**も作る。
 * 名前解決の失敗はスキームを含まない形（`Unable to resolve host "<ホスト>": ...`）で
 * ホスト名を持つため、値そのものだけを見ていると通信先が伏せられずに残る。
 *
 * 値そのもののパターンを先に並べる。ホスト名のパターンを先に当てると
 * `https://` が残り、続くパス・クエリを取りこぼす。
 */
internal fun buildSecretPatterns(secrets: List<String>): List<Regex> {
    val values = secrets.filter { it.length >= MIN_SECRET_LENGTH }
    val fullValuePatterns = values.map { Regex(Regex.escape(it) + URL_TAIL_PATTERN) }
    val hostPatterns = values
        .mapNotNull { hostOf(it) }
        .distinct()
        .filter { it.length >= MIN_SECRET_LENGTH }
        .map { Regex(HOST_BOUNDARY_PATTERN + Regex.escape(it) + URL_TAIL_PATTERN) }
    return fullValuePatterns + hostPatterns
}

/** URLでないシークレット（DialogflowのプロジェクトIDなど）はホスト名を持たない */
private fun hostOf(value: String): String? = try {
    URL(value).host?.takeIf { it.isNotBlank() }
} catch (e: MalformedURLException) {
    null
}

/** [patterns]に一致する箇所とAPIキーを[MASK]に置き換える */
internal fun redactSecrets(message: String, patterns: List<Regex>): String {
    var redacted = message
    patterns.forEach { redacted = it.replace(redacted, MASK) }
    return API_KEY_PATTERN.replace(redacted, MASK)
}

/** クラッシュレポートに添えるキー */
object CrashlyticsKey {
    const val AI_MODEL = "ai_model"
    const val UI_MODE = "ui_mode"
    const val AD_NETWORK = "ad_network"

    /**
     * 失敗した応答のうち、ストリーミングで届いていた文字数。
     * 「応答ゼロ」と「途中まで届いて失敗」を区別するために持つ（#100）。
     */
    const val STREAMED_CHARS = "streamed_chars"

    /** 直前に通知されたメモリ不足の水位。プロセスを殺された原因を追うために持つ（#101） */
    const val LAST_TRIM_LEVEL = "last_trim_level"
}
