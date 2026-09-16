package com.airgesture.app.command

import com.airgesture.app.accessibility.*
import com.airgesture.app.cursor.CoordinateMapper

/** Both voice and hand commands use the same freshness, screen and single-action checks. */
class SystemActionController(private val coordinator:ActionCoordinator){
    fun submit(command:ControlCommand,allowed:Boolean,screen:ScreenGeometry,inset:Float=0f,
        nodeSelection:NodeSelection?=null,dispatch:(SystemAction,(ActionResult)->Unit)->Boolean):Boolean {
        val kind=command.systemAction ?: return false
        val target=command.point?.let{CoordinateMapper().toPixels(it,screen.width,screen.height,inset)}
        return coordinator.submit(SystemAction(kind,target,screen,command.detectedAt,nodeSelection),allowed,screen,dispatch)
    }
}
