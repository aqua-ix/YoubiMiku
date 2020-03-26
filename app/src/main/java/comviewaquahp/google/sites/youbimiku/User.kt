/**
 * Created by Aqua-ix on 2020/03/27.
 */

package comviewaquahp.google.sites.youbimiku

import android.graphics.Bitmap
import com.github.bassaer.chatmessageview.model.IChatUser

class User internal constructor(private val id: Int, private var name: String?, private var icon: Bitmap?) : IChatUser {

    override fun getId(): String {
        return id.toString()
    }

    override fun getName(): String? {
        return name
    }

    override fun getIcon(): Bitmap? {
        return icon
    }

    override fun setIcon(bmp: Bitmap) {
        this.icon = bmp
    }

    fun setName(name: String) {
        this.name = name
    }

}