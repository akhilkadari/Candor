package com.google.ai.edge.gallery

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.ai.edge.gallery.proto.CheckInCollection
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object CheckInSerializer : Serializer<CheckInCollection> {
  override val defaultValue: CheckInCollection = CheckInCollection.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): CheckInCollection {
    try {
      return CheckInCollection.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read proto.", exception)
    }
  }

  override suspend fun writeTo(t: CheckInCollection, output: OutputStream) = t.writeTo(output)
}
