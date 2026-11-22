package com.hcexport.ui

/** A request to open the live workout for one workout, from a notification or
 *  from the watch starting a session. */
data class LiveJoinRequest(
    val date: String,
    val typeKey: String,
    val nonce: Long,
)
