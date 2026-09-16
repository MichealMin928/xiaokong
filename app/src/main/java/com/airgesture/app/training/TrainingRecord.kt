package com.airgesture.app.training

import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.gesture.HandObservation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.BufferedWriter

/** JSONL contains numerical model output, never image buffers. Partial clips remain readable after interruption. */
class TrainingRecord(val file:File,header:JSONObject) : AutoCloseable {
    private val writer:BufferedWriter
    init {file.parentFile?.mkdirs();writer=file.bufferedWriter();append(header.put("type","header").put("schema",1))}
    @Synchronized fun append(value:JSONObject){writer.write(value.toString());writer.newLine();writer.flush()}
    @Synchronized override fun close(){writer.close()}
    companion object {
        fun read(file:File):List<JSONObject> = file.bufferedReader().useLines{lines->lines.mapNotNull{runCatching{JSONObject(it)}.getOrNull()}.toList()}
        fun point(value:CursorPoint?):Any=if(value==null)JSONObject.NULL else JSONArray().put(value.x).put(value.y)
        fun observation(row:JSONObject):HandObservation? {
            if(!row.optBoolean("accepted"))return null
            val mapped=row.optJSONArray("cursor") ?: return null
            val array=row.optJSONArray("landmarks") ?: return null
            val points=(0 until array.length()).map{i->val p=array.getJSONArray(i);CursorPoint(p.getDouble(0).toFloat(),p.getDouble(1).toFloat())}
            return HandObservation.from(points,row.getInt("width"),row.getInt("height"),row.getLong("t"),CursorPoint(mapped.getDouble(0).toFloat(),mapped.getDouble(1).toFloat()))
        }
        fun annotate(file:File,confirmed:Boolean){File(file.parentFile,file.nameWithoutExtension+".review.json").writeText(JSONObject().put("performed_as_prompt",confirmed).put("updated_at",System.currentTimeMillis()).toString(2))}
    }
}
