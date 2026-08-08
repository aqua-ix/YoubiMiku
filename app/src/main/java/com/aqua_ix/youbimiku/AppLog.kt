package com.aqua_ix.youbimiku

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.PrintWriter
import java.io.StringWriter

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

    private const val MASK = "***"

    /**
     * 伏せる対象として扱う値の最短の長さ。
     * 空文字や1文字を[String.replace]の対象にすると、文字の間すべてに[MASK]が入って
     * ログが壊れるため、短すぎる値（未設定のシークレットなど）は対象にしない。
     */
    private const val MIN_SECRET_LENGTH = 8

    /** 原因を辿る深さの上限。causeが循環していても止まるようにする */
    private const val MAX_CAUSE_DEPTH = 10

    /**
     * ログとCrashlyticsから伏せる値。
     * `secrets.properties` の値は`BuildConfig`に埋め込まれ、例外のメッセージや
     * URLのログにそのまま現れるため、通信先を特定できる項目をまとめて対象にする。
     */
    private val secrets: List<String> by lazy {
        listOf(
            BuildConfig.TRANSLATE_END_POINT,
            BuildConfig.REPORT_END_POINT,
            BuildConfig.AVATAR_BASE_URL,
            BuildConfig.DIALOGFLOW_PROJECT_ID,
        ).filter { it.length >= MIN_SECRET_LENGTH }
    }

    /**
     * OpenAIのAPIキー。Firebase経由で受け取るため`BuildConfig`には無く固定値で持てないが、
     * 万一ログや例外に現れても値が残らないようにする。
     */
    private val apiKeyPattern = Regex("sk-[A-Za-z0-9_-]{8,}")

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

    /** シークレットを[MASK]に置き換える */
    private fun redact(message: String): String {
        var redacted = message
        secrets.forEach { redacted = redacted.replace(it, MASK) }
        return apiKeyPattern.replace(redacted, MASK)
    }

    /**
     * Crashlyticsに送れる形の例外を返す。
     *
     * [FirebaseCrashlytics.recordException]は例外のメッセージもそのまま送るため、
     * シークレットを含む場合だけ伏せた写しに差し替える。含まない場合は元の例外を返し、
     * Crashlytics側で本来の型でまとめられるようにする。
     */
    private fun sanitize(throwable: Throwable): Throwable =
        if (containsSecret(throwable)) redactedCopy(throwable, 0) else throwable

    private fun containsSecret(throwable: Throwable): Boolean =
        causesOf(throwable).any { cause ->
            val message = cause.message
            message != null && redact(message) != message
        }

    private fun causesOf(throwable: Throwable): Sequence<Throwable> =
        generateSequence(throwable) { if (it.cause === it) null else it.cause }
            .take(MAX_CAUSE_DEPTH)

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
