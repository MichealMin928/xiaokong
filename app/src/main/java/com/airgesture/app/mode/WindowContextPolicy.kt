package com.airgesture.app.mode

object WindowContextPolicy {
    /** ColorOS camera-status announcement has no window. It must not replace the real foreground app.
     * Real notification-shade/keyguard windows still suspend the scene through the normal whitelist. */
    fun changesForeground(packageName:String,className:String?,windowId:Int,fullScreen:Boolean):Boolean =
        className!="android.widget.Toast" && !(packageName=="com.android.systemui" && className=="android.view.View" && windowId<0 && !fullScreen)
}
