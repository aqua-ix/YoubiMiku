package com.aqua_ix.youbimiku.database

import com.aqua_ix.youbimiku.User
import com.github.bassaer.chatmessageview.model.Message
import java.util.Calendar

fun messageToEntity(message: Message): MessageEntity {
    return MessageEntity(
        userId = message.user.getId().toInt(),
        isRightMessage = message.isRight,
        text = message.text.toString(),
        hideIcon = message.isIconHided,
        sendTime = message.sendTime.timeInMillis
    )
}

fun entityToMessage(entity: MessageEntity, user: User): Message {
    return Message.Builder()
        .setUser(user)
        .setRight(entity.isRightMessage)
        .setText(entity.text)
        .hideIcon(entity.hideIcon)
        .setSendTime(Calendar.getInstance().apply { timeInMillis = entity.sendTime })
        .build()
}