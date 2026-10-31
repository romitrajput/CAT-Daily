package com.catdaily.app.remote

import android.content.Context
import com.catdaily.app.data.LeaderRow
import com.catdaily.app.data.Question
import com.catdaily.app.data.Visual
import com.catdaily.app.render.PdfCropRenderer
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Firestore-backed leaderboard + question-bank sync. Everything here is optional: if the app was
 * built without google-services.json, [enabled] is false and the app runs fully offline.
 *
 * Collections
 *   users/{uid}                     name, streak, best, days, attempted, correct, points
 *   daily/{yyyy-MM-dd}/entries/{uid} name, attempted, correct, points, completed, seconds
 *   bank/{questionId}               imported question JSON + base64 strips   (admin writes)
 *   bankGroups/{groupId}            passage / DILR-set strips                (admin writes)
 *   meta/answers  meta/overrides    answer key & tag edits                   (admin writes)
 *   meta/owner                      {uid} of the main user (first claim wins)
 */
class Cloud(ctx: Context) {
    val enabled: Boolean = FirebaseApp.getApps(ctx).isNotEmpty()
    private val db get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    val uid: String? get() = if (enabled) auth.currentUser?.uid else null

    suspend fun signIn(): String? {
        if (!enabled) return null
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user?.uid
    }

    // ---------------------------------------------------------------- progress
    suspend fun pushProgress(
        date: String, name: String, attempted: Int, correct: Int, points: Int, completed: Boolean, seconds: Int,
        streak: Int, best: Int, days: Int, totalAttempted: Int, totalCorrect: Int, totalPoints: Int
    ) {
        val id = signIn() ?: return
        db.collection("daily").document(date).collection("entries").document(id).set(
            mapOf(
                "name" to name, "attempted" to attempted, "correct" to correct, "points" to points,
                "completed" to completed, "seconds" to seconds, "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        db.collection("users").document(id).set(
            mapOf(
                "name" to name, "streak" to streak, "best" to best, "days" to days,
                "attempted" to totalAttempted, "correct" to totalCorrect, "points" to totalPoints,
                "lastDate" to date, "updatedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge()
        ).await()
    }

    suspend fun dailyBoard(date: String): List<LeaderRow> {
        val me = signIn() ?: return emptyList()
        val snap = db.collection("daily").document(date).collection("entries")
            .orderBy("points", Query.Direction.DESCENDING).limit(200).get().await()
        return snap.documents.map { d -> Triple(d, d.getBoolean("completed") == true, d.getLong("seconds") ?: 0L) }
            .sortedWith(
                compareByDescending<Triple<DocumentSnapshot, Boolean, Long>> { it.first.getLong("points") ?: 0L }
                    .thenByDescending { it.second }.thenBy { it.third }
            )
            .map { (d, done, secs) ->
                LeaderRow(
                    d.id, d.getString("name") ?: "Anonymous", (d.getLong("points") ?: 0).toInt(),
                    "${d.getLong("attempted") ?: 0}/15 · ${d.getLong("correct") ?: 0} correct · ${secs / 60}m" + if (done) " ✓" else "",
                    d.id == me
                )
            }
    }

    suspend fun usersBoard(field: String): List<LeaderRow> {
        val me = signIn() ?: return emptyList()
        val snap = db.collection("users").orderBy(field, Query.Direction.DESCENDING).limit(200).get().await()
        return snap.documents.map { d ->
            LeaderRow(
                d.id, d.getString("name") ?: "Anonymous", (d.getLong(field) ?: 0).toInt(),
                "best ${d.getLong("best") ?: 0} · ${d.getLong("days") ?: 0} days · ${d.getLong("correct") ?: 0} correct",
                d.id == me
            )
        }
    }

    // ---------------------------------------------------------------- admin
    /** First person to claim becomes the owner; returns true if *this* user is the owner. */
    suspend fun claimOwner(): Boolean {
        val id = signIn() ?: return false
        val ref = db.collection("meta").document("owner")
        val cur = ref.get().await()
        if (!cur.exists()) { ref.set(mapOf("uid" to id)).await(); return true }
        return cur.getString("uid") == id
    }

    suspend fun publishImported(
        qs: List<Question>, groups: Map<String, Visual>, renderer: PdfCropRenderer, onProgress: (Int, Int) -> Unit
    ) {
        signIn() ?: return
        val total = qs.size + groups.size; var done = 0
        for ((gid, v) in groups) {
            val imgs = renderer.encodeStrips(v)
            db.collection("bankGroups").document(gid).set(mapOf("images" to imgs, "regions" to v.regions.size)).await()
            onProgress(++done, total)
        }
        for (q in qs) {
            val imgs = renderer.encodeStrips(q.visual)
            db.collection("bank").document(q.id).set(mapOf("json" to q.toJson().toString(), "images" to imgs)).await()
            onProgress(++done, total)
        }
        db.collection("meta").document("bank").set(mapOf("updatedAt" to FieldValue.serverTimestamp(), "count" to FieldValue.increment(qs.size.toLong())), SetOptions.merge()).await()
    }

    /** Pulls imported questions (with images) published by the admin. Returns null when nothing is published. */
    suspend fun pullBank(): JSONObject? {
        signIn() ?: return null
        val qs = db.collection("bank").get().await()
        if (qs.isEmpty) return null
        val arr = JSONArray()
        for (d in qs.documents) {
            val o = JSONObject(d.getString("json") ?: continue)
            o.put("pdf", JSONObject.NULL)
            @Suppress("UNCHECKED_CAST")
            o.put("images", JSONArray((d.get("images") as? List<String>) ?: emptyList<String>()))
            arr.put(o)
        }
        val groups = JSONObject()
        for (d in db.collection("bankGroups").get().await().documents) {
            @Suppress("UNCHECKED_CAST")
            val imgs = (d.get("images") as? List<String>) ?: emptyList()
            groups.put(d.id, JSONObject().put("pdf", JSONObject.NULL).put("regions", JSONArray()).put("images", JSONArray(imgs)))
        }
        return JSONObject().put("questions", arr).put("groups", groups)
    }

    suspend fun publishAnswers(answers: Map<String, String>) {
        signIn() ?: return
        db.collection("meta").document("answers").set(answers, SetOptions.merge()).await()
    }

    suspend fun pullAnswers(): Map<String, String> {
        signIn() ?: return emptyMap()
        val d = db.collection("meta").document("answers").get().await()
        return d.data?.mapValues { it.value.toString() } ?: emptyMap()
    }

    suspend fun publishOverrides(o: JSONObject) {
        signIn() ?: return
        db.collection("meta").document("overrides").set(mapOf("json" to o.toString())).await()
    }

    suspend fun pullOverrides(): JSONObject? {
        signIn() ?: return null
        val s = db.collection("meta").document("overrides").get().await().getString("json") ?: return null
        return JSONObject(s)
    }
}
