package com.example.callmind.data.local.entities

data class GroupedCallLog(
    val lastCall: CallRecordEntity,
    val calls: List<CallRecordEntity>
)

sealed class CallLogDisplayItem {
    data class Header(val title: String) : CallLogDisplayItem()
    data class CallGroup(val group: GroupedCallLog) : CallLogDisplayItem()
}
