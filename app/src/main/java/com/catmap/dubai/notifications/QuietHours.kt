package com.catmap.dubai.notifications

object QuietHours {
    /** Start inclusive, end exclusive. Equal endpoints mean a full-day quiet window. */
    fun contains(minute:Int,start:Int,end:Int):Boolean {
        require(minute in 0..1439 && start in 0..1439 && end in 0..1439)
        return if(start==end) true else if(start<end) minute in start until end else minute>=start || minute<end
    }
}
