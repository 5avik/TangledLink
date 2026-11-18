package com.v2ray.ang.handler

/** Not a data class: tokens must never appear in generated toString output. */
class EmailAuthResult(val required:Boolean=false,val retrySeconds:Long=60,val token:String?=null,val expiresAt:Long=0)
