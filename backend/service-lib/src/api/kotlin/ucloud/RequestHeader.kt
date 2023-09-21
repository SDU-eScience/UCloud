// automatically generated by the FlatBuffers compiler, do not modify

package ucloud

import com.google.flatbuffers.BaseVector
import com.google.flatbuffers.BooleanVector
import com.google.flatbuffers.ByteVector
import com.google.flatbuffers.Constants
import com.google.flatbuffers.DoubleVector
import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.FloatVector
import com.google.flatbuffers.LongVector
import com.google.flatbuffers.StringVector
import com.google.flatbuffers.Struct
import com.google.flatbuffers.Table
import com.google.flatbuffers.UnionVector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sign

@Suppress("unused")
@kotlin.ExperimentalUnsignedTypes
class RequestHeader : Table() {

    fun __init(_i: Int, _bb: ByteBuffer)  {
        __reset(_i, _bb)
    }
    fun __assign(_i: Int, _bb: ByteBuffer) : RequestHeader {
        __init(_i, _bb)
        return this
    }
    val call : UShort
        get() {
            val o = __offset(4)
            return if(o != 0) bb.getShort(o + bb_pos).toUShort() else 0u
        }
    val stream : UShort
        get() {
            val o = __offset(6)
            return if(o != 0) bb.getShort(o + bb_pos).toUShort() else 0u
        }
    val project : UByte
        get() {
            val o = __offset(8)
            return if(o != 0) bb.get(o + bb_pos).toUByte() else 0u
        }
    companion object {
        fun validateVersion() = Constants.FLATBUFFERS_23_3_3()
        fun getRootAsRequestHeader(_bb: ByteBuffer): RequestHeader = getRootAsRequestHeader(_bb, RequestHeader())
        fun getRootAsRequestHeader(_bb: ByteBuffer, obj: RequestHeader): RequestHeader {
            _bb.order(ByteOrder.LITTLE_ENDIAN)
            return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb))
        }
        fun createRequestHeader(builder: FlatBufferBuilder, call: UShort, stream: UShort, project: UByte) : Int {
            builder.startTable(3)
            addStream(builder, stream)
            addCall(builder, call)
            addProject(builder, project)
            return endRequestHeader(builder)
        }
        fun startRequestHeader(builder: FlatBufferBuilder) = builder.startTable(3)
        fun addCall(builder: FlatBufferBuilder, call: UShort) = builder.addShort(0, call.toShort(), 0)
        fun addStream(builder: FlatBufferBuilder, stream: UShort) = builder.addShort(1, stream.toShort(), 0)
        fun addProject(builder: FlatBufferBuilder, project: UByte) = builder.addByte(2, project.toByte(), 0)
        fun endRequestHeader(builder: FlatBufferBuilder) : Int {
            val o = builder.endTable()
            return o
        }
    }
}