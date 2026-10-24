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

data class MonthlyProjection(val label: String, val commitmentTotal: Int, val activeCommitments: Int)

data class CategoryLimit(val category: String, val amount: Int)

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

    fun categoryLimits(): List<CategoryLimit> = readArray("category_limits") { item ->
        CategoryLimit(item.optString("category", "otros"), item.optInt("amount", 0))
    }.filter { it.amount > 0 }

    fun categoryLimit(category: String): Int = categoryLimits().firstOrNull { it.category == category }?.amount ?: 0

    fun setCategoryLimit(category: String, amount: Int) {
        val values = categoryLimits().filterNot { it.category == category }.toMutableList()
        if (amount > 0) values += CategoryLimit(category, amount)
        writeArray("category_limits", JSONArray().apply { values.forEach { put(JSONObject().apply { put("category", it.category); put("amount", it.amount) }) } })
    }

    fun projections(months: Int = 6): List<MonthlyProjection> {
        val calendar = java.util.Calendar.getInstance()
        return (0 until months).map { offset ->
            val monthCalendar = calendar.clone() as java.util.Calendar
            monthCalendar.add(java.util.Calendar.MONTH, offset)
            val active = commitments().filter { commitment ->
                !commitment.hasInstallments || commitment.currentInstallment + offset < commitment.totalInstallments
            }
            MonthlyProjection(
                label = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale("es", "CL")).format(monthCalendar.time),
                commitmentTotal = active.sumOf { it.amount },
                activeCommitments = active.size
            )
        }
    }

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
        put("category_limits", JSONArray().apply { categoryLimits().forEach { put(JSONObject().apply { put("category", it.category); put("amount", it.amount) }) } })
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
        val limits = root.optJSONArray("category_limits")
        if (limits != null) {
            val mergedLimits = categoryLimits().associateBy { it.category }.toMutableMap()
            for (index in 0 until limits.length()) {
                val item = limits.optJSONObject(index) ?: continue
                val category = item.optString("category", "otros")
                val amount = item.optInt("amount", 0)
                if (category !in mergedLimits && amount > 0) mergedLimits[category] = CategoryLimit(category, amount)
            }
            writeArray("category_limits", JSONArray().apply {
                mergedLimits.values.sortedBy { it.category }.forEach { put(JSONObject().apply { put("category", it.category); put("amount", it.amount) }) }
            })
        }
        writeArray("incomes", JSONArray().apply { newIncomes.forEach { put(it.toJson()) } })
        writeArray("commitments", JSONArray().apply { newCommitments.forEach { put(it.toJson()) } })
        return imported
    }

    fun cloudData(): Map<String, Any> = mapOf(
        "schema_version" to 1,
        "monthly_goal" to monthlyGoal(),
        "category_limits" to categoryLimits().map { mapOf("category" to it.category, "amount" to it.amount) },
        "incomes" to incomes().map { mapOf("id" to it.id, "name" to it.name, "amount" to it.amount, "recurring" to it.recurring) },
        "commitments" to commitments().map {
            mapOf("id" to it.id, "name" to it.name, "amount" to it.amount, "current_installment" to it.currentInstallment,
                "total_installments" to it.totalInstallments, "category" to it.category, "recurring" to it.recurring)
        }
    )

    fun importCloudData(data: Map<String, Any?>): Int {
        if ((data["schema_version"] as? Number)?.toInt() != 1) return 0
        val root = JSONObject().apply {
            put("schema_version", 1)
            put("monthly_goal", (data["monthly_goal"] as? Number)?.toInt() ?: 0)
            put("category_limits", JSONArray().apply {
                (data["category_limits"] as? List<*>)?.forEach { item ->
                    (item as? Map<*, *>)?.let { put(JSONObject(it.mapKeys { entry -> entry.key.toString() })) }
                }
            })
            put("incomes", JSONArray().apply {
                (data["incomes"] as? List<*>)?.forEach { item ->
                    (item as? Map<*, *>)?.let { put(JSONObject(it.mapKeys { entry -> entry.key.toString() })) }
                }
            })
            put("commitments", JSONArray().apply {
                (data["commitments"] as? List<*>)?.forEach { item ->
                    (item as? Map<*, *>)?.let { put(JSONObject(it.mapKeys { entry -> entry.key.toString() })) }
                }
            })
        }
        return importJson(root)
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
