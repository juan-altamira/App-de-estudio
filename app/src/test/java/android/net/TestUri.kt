package android.net

import android.os.Parcel

class TestUri(
    private val rawValue: String,
) : Uri() {
    override fun toString(): String = rawValue

    override fun buildUpon(): Builder = unsupported()

    override fun getAuthority(): String? = null

    override fun getEncodedAuthority(): String? = null

    override fun getEncodedFragment(): String? = null

    override fun getEncodedPath(): String? = null

    override fun getEncodedQuery(): String? = null

    override fun getEncodedSchemeSpecificPart(): String = rawValue

    override fun getEncodedUserInfo(): String? = null

    override fun getFragment(): String? = null

    override fun getHost(): String? = null

    override fun getLastPathSegment(): String? = null

    override fun getPath(): String? = null

    override fun getPathSegments(): List<String> = emptyList()

    override fun getPort(): Int = -1

    override fun getQuery(): String? = null

    override fun getScheme(): String? = "content"

    override fun getSchemeSpecificPart(): String = rawValue

    override fun getUserInfo(): String? = null

    override fun isHierarchical(): Boolean = true

    override fun isRelative(): Boolean = false

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) = Unit

    private fun <T> unsupported(): T =
        throw UnsupportedOperationException("TestUri only supports toString in local unit tests.")
}
