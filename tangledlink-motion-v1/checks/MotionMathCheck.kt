package com.v2ray.ang.ui.motion

import kotlin.math.abs

fun main() {
    var checks=0
    fun expect(value:Boolean) { check(value); checks++ }
    fun close(a:Float,b:Float) = abs(a-b)<.00001f
    expect(MotionMath.phase(Float.NaN)==0f)
    expect(MotionMath.phase(Float.POSITIVE_INFINITY)==0f)
    expect(MotionMath.phase(1f)==0f)
    expect(close(MotionMath.phase(-.25f),.75f))
    for(i in 0 until 1000) expect(MotionMath.seed(i) in 0f..<1f)
    for(i in 0 until 56) {
        val a=MotionMath.rain(i,0f); val b=MotionMath.rain(i,1f)
        expect(a==b)
        val pa=MotionMath.petal(i,0f); val pb=MotionMath.petal(i,1f)
        expect(pa==pb)
        for(frame in 0 until 288) {
            val p=frame/288f
            val rain=MotionMath.rain(i,p)
            expect(rain.x in 0f..1f && rain.y in -.051f..1.051f && rain.radius>0f && rain.alpha in 0f..1f)
            val petal=MotionMath.petal(i,p)
            expect(petal.x in -.036f..1.036f && petal.y in -.081f..1.081f && petal.radius>0f && petal.alpha in 0f..1f && petal.angle.isFinite())
        }
    }
    expect(MotionMath.allowed(true,true,false,1f,false))
    expect(!MotionMath.allowed(false,true,false,1f,false))
    expect(!MotionMath.allowed(true,false,false,1f,false))
    expect(!MotionMath.allowed(true,true,true,1f,false))
    expect(!MotionMath.allowed(true,true,false,0f,false))
    expect(!MotionMath.allowed(true,true,false,Float.NaN,false))
    expect(!MotionMath.allowed(true,true,false,1f,true))
    println("Motion math: $checks checks passed")
}
