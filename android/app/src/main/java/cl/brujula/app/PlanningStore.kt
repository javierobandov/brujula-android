package cl.brujula.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PlannedIncome(
    val id: String,
    val name: String,
    val amount: Int,
    val recurring: Boolean = true
)

data class Commitment(
    val id: String,
    val name: String,
    val amount: Int,
    val currentInstallment: Int = 0,
    val totalInstallments: Int = 0,
    val category: String = "servicios",
    val recurring: Boolean = true
) {
    val hasInstallments: Boolean get() = totalInstallments > 0
    val remainingInstallments: Int get() = (totalInstallments - currentInstallment).coerceAtLeast(0)
    val isActive: Boolean get() = !hasInstallments || remainingInstallments > 0
}

/** Local-first planning data. It deliberately stores no bank credentials or notification text. */
class PlanningStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("brujula_planning", Context.MODE_PRIVATE)

    fun incomes(): List<PlannedIncome> = readArray("incomes") { item ->
        PlannedIncome(
            id = item.optString("id"),
            name = item.optString("name"),
            amount = item.optInt("amount"),
            recurring = item.optBoolean("recurring", true)
        )
    }

    fun commitments(): List<Commitment> = readArray("commitments") { item ->
        Commitment(
            id = item.optString("id"),
            name = item.optString("name"),
            amount = item.optInt("amount"),
            currentInstallment = item.optInt("current_installment"),
            totalInstallments = item.optInt("total_installments"),
            category = item.optString("category", "servicios"),
            recurring = item.optBoolean("recurring", true)
        )
    }

    fun monthlyGoal(): Int = prefs.getInt("monthly_goal", 0)
    fun setMonthlyGoal(value: Int) { prefs.edit().putInt("monthly_goal", value.coerceAtLeast(0)).apply() }

    fun addIncome(name: String, amount: Int, recurring: Boolean = true) {
        val values = incomes().toMutableList()
        values += PlannedIncome(UUID.randomUUID().toString(), name.trim(), amount, recurring)
        writeArray("incomes", JSONArray().apply { values.forEach { put(it.toJson()) } })
    }

    fun addCommitment(
        name: String,
        amount: Int,
        currentInstallment: Int,
        totalInstallments: Int,
        category: String,
        recurring: Boolean = true
    ) {
        val values = commitments().toMutableList()
        values += Commitment(UUID.randomUUID().toString(), name.trim(), amount, currentInstallment, totalInstallments, category, recurring)
        writeArray("commitments", JSONArray().apply { values.forEach { put(it.toJson()) } })
    }

    fun deleteIncome(id: String) = writeArray("incomes", JSONArray().apply {
        incomes().filterNot { it.id == id }.forEach { put(it.toJson()) }
    })

    fun deleteCommitment(id: String) = writeArray("commitments", JSONArray().apply {
        commitments().filterNot { it.id == id }.forEach { put(it.toJson()) }
    })

    fun exportJson(): JSONObject = JSONObject().apply {
        put("schema_version", 1)
        put("monthly_goal", monthlyGoal())
        put("incomes", JSONArray().apply { this@PlanningStore.incomes().forEach { put(it.toJson()) } })
        put("commitments", JSONArray().apply { this@PlanningStore.commitments().forEach { put(it.toJson()) } })
    }

    fun importJson(root: JSONObject): Int {
        if (root.optInt("schema_version", -1) != 1) return 0
        val incomesArray = root.optJSONArray("incomes") ?: JSONArray()
        val commitmentsArray = root.optJSONArray("commitments") ?: JSONArray()
        val existingIncomeIds = incomes().map { it.id }.toSet()
        val existingCommitmentIds = commitments().map { it.id }.toSet()
        val newIncomes = incomes().toMutableList()
        val newCommitments = commitments().toMutableList()
        var imported = 0
        for (index in 0 until incomesArray.length()) {
            val item = incomesArray.optJSONObject(index) ?: continue
            val value = PlannedIncome(item.optString("id"), item.optString("name"), item.optInt("amount"), item.optBoolean("recurring", true))
            if (value.id.isNotBlank() && value.id !in existingIncomeIds) { newIncomes += value; imported++ }
        }
        for (index in 0 until commitmentsArray.length()) {
            val item = commitmentsArray.optJSONObject(index) ?: continue
            val value = Commitment(item.optString("id"), item.optString("name"), item.optInt("amount"), item.optInt("current_installment"), item.optInt("total_installments"), item.optString("category", "servicios"), item.optBoolean("recurring", true))
            if (value.id.isNotBlank() && value.id !in existingCommitmentIds) { newCommitments += value; imported++ }
        }
        if (root.has("monthly_goal") && monthlyGoal() == 0) setMonthlyGoal(root.optInt("monthly_goal"))
        writeArray("incomes", JSONArray().apply { newIncomes.forEach { put(it.toJson()) } })
        writeArray("commitments", JSONArray().apply { newCommitments.forEach { put(it.toJson()) } })
        return imported
    }

    private fun <T> readArray(key: String, mapper: (JSONObject) -> T): List<T> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(mapper) }
    }.getOrDefault(emptyList())

    private fun writeArray(key: String, value: JSONArray) { prefs.edit().putString(key, value.toString()).apply() }

    private fun PlannedIncome.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("amount", amount); put("recurring", recurring)
    }

    private fun Commitment.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("amount", amount)
        put("current_installment", currentInstallment); put("total_installments", totalInstallments)
        put("category", category); put("recurring", recurring)
    }
}
