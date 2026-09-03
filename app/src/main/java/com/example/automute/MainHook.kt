package com.example.automute

import android.content.Context
import android.media.AudioManager
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        val pkg = lpparam.packageName.lowercase()
        if (!pkg.contains("incallui") && !pkg.contains("dialer") && !pkg.contains("phone") && !pkg.contains("telecom")) {
            return
        }

        XposedBridge.log("DialerUnlocker: Attached to Dialer app -> $pkg")

        try {
            val hookEnabled = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val enabled = param.args[0] as Boolean
                    if (!enabled) {
                        val view = param.thisObject as View
                        if (isTargetView(view)) {
                            param.args[0] = true
                            view.alpha = 1.0f
                        }
                    }
                }
            }

            XposedHelpers.findAndHookMethod(View::class.java, "setEnabled", Boolean::class.javaPrimitiveType, hookEnabled)
            XposedHelpers.findAndHookMethod(View::class.java, "setClickable", Boolean::class.javaPrimitiveType, hookEnabled)
            
            XposedHelpers.findAndHookMethod(View::class.java, "setAlpha", Float::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val alpha = param.args[0] as Float
                    if (alpha < 1.0f) {
                        val view = param.thisObject as View
                        if (isTargetView(view)) {
                            param.args[0] = 1.0f
                        }
                    }
                }
            })

            // Catch views that are disabled from XML
            XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (isTargetView(view)) {
                        if (!view.isEnabled) view.isEnabled = true
                        if (!view.isClickable) view.isClickable = true
                        if (view.alpha < 1.0f) view.alpha = 1.0f
                    }
                }
            })

            XposedHelpers.findAndHookMethod(View::class.java, "performClick", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (isTargetMuteButton(view)) {
                        XposedBridge.log("DialerUnlocker: Mute button clicked by user!")
                        val am = view.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val currentState = am.isMicrophoneMute
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (am.isMicrophoneMute == currentState) {
                                am.isMicrophoneMute = !currentState
                                XposedBridge.log("DialerUnlocker: Force-toggled AudioManager to ${!currentState}")
                            }
                        }, 50)
                    }
                }
            })

        } catch (e: Throwable) {
            XposedBridge.log("DialerUnlocker Critical Error: ${e.message}")
        }
    }

    private fun isTargetView(view: View): Boolean {
        val clsName = view.javaClass.simpleName.lowercase()
        if (clsName.contains("button") || clsName.contains("toggle")) return true
        
        try {
            if (view.id != View.NO_ID) {
                val idName = view.context.resources.getResourceEntryName(view.id)?.lowercase() ?: ""
                if (idName.contains("mute") || idName.contains("mic") || idName.contains("audio") 
                    || idName.contains("btn") || idName.contains("action") || idName.contains("incall")) {
                    return true
                }
            }
        } catch (e: Exception) {}
        
        try {
            val desc = view.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains("mute") || desc.contains("mic") || desc.contains("كتم") || desc.contains("ميك") || desc.contains("صوت")) {
                return true
            }
        } catch (e: Exception) {}
        
        return false
    }

    private fun isTargetMuteButton(view: View): Boolean {
        try {
            if (view.id != View.NO_ID) {
                val idName = view.context.resources.getResourceEntryName(view.id)?.lowercase() ?: ""
                if (idName.contains("mute") || idName.contains("mic") || idName.contains("audio")) return true
            }
        } catch (e: Exception) {}
        
        try {
            val desc = view.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains("mute") || desc.contains("mic") || desc.contains("كتم") || desc.contains("ميك") || desc.contains("صوت")) return true
        } catch (e: Exception) {}
        
        return false
    }
}
