package com.capstone.mybottomnav.response

import com.google.gson.annotations.SerializedName

data class RegisterResponses(

    @field:SerializedName("error")
    val error: Boolean? = null,

    @field:SerializedName("message")
    val message: String? = null
)