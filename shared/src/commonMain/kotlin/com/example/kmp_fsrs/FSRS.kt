package chat.sdk.android_fsrs


import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

public enum class Status(val value: String) {
    New("New"), Learning("Learning"), Review("Review"), Relearning("Relearning");
    companion object {
        fun from(value: String): Status {
            return Status.entries.find { it.value == value } ?: Status.New
        }
    }
}

enum class Rating(val description: String) {
    Again("Again"), Hard("Hard"), Good("Good"), Easy("Easy");

    fun value(): Int {
        return ordinal + 1
    }
}

class ReviewLog(
    var rating: Rating,
    var elapsedDays: Double,
    var scheduledDays: Double,
    var review: LocalDateTime,
    var status: Status
) {
    fun data(): Map<String, Any> {
        return mapOf(
            "rating" to rating,
            "elapsedDays" to elapsedDays,
            "scheduledDays" to scheduledDays,
            "review" to review,
            "state" to status.value
        )
    }
}

class FSRSCard {

    var due: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    var stability: Double = 0.0
    var difficulty: Double = 0.0
    var elapsedDays: Double = 0.0
    var scheduledDays: Double = 0.0
    var reps: Int = 0
    var lapses: Int = 0
    var status: Status = Status.New
    var lastReview: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    //    fun retrievability(now: Date): Double? {
//        var retrievability: Double? = null
//        if (status == Status.Review) {
//            val elapsedDays = max(0.0, (now.time - lastReview.time) / (Const.secondsInDay))
//            retrievability = exp(log(0.9) * elapsedDays / stability)
//        }
//        return retrievability
//    }

    public fun clone(): FSRSCard {
        val card = FSRSCard()
        card.due = due
        card.stability = stability
        card.difficulty = difficulty
        card.elapsedDays = elapsedDays
        card.scheduledDays = scheduledDays
        card.reps = reps
        card.lapses = lapses
        card.status = status
        card.lastReview = lastReview
        return card
    }

    fun data(): Map<String, Any> {
        return mapOf(
            "due" to due,
            "stability" to stability,
            "difficulty" to difficulty,
            "elapsed_days" to elapsedDays,
            "scheduled_days" to scheduledDays,
            "reps" to reps,
            "lapses" to lapses,
            "state" to status.value,
            "last_review" to lastReview
        )
    }

    fun printLog() {
        try {
            val data = data()
            println(data)
        } catch (e: Exception) {
            println("Error serializing JSON: $e")
        }
    }
}

class SchedulingInfo {

    var card: FSRSCard
    var reviewLog: ReviewLog
    constructor(card: FSRSCard, reviewLog: ReviewLog) {
        this.card = card
        this.reviewLog = reviewLog
    }

    constructor(rating: Rating, reference: FSRSCard, current: FSRSCard, review: LocalDateTime) {
        this.card = reference
        this.reviewLog = ReviewLog(rating, reference.scheduledDays, current.elapsedDays, review, current.status)
    }
    fun data(): Map<String, Map<String, Any>> {
        return mapOf(
            "log" to reviewLog.data(),
            "card" to card.data()
        )
    }
}

class SchedulingCards(card: FSRSCard) {

    var again: FSRSCard
    var hard: FSRSCard
    var good: FSRSCard
    var easy: FSRSCard

    init {
        this.again = card.clone()
        this.hard = card.clone()
        this.good = card.clone()
        this.easy = card.clone()
    }

    fun updateStatus(status: Status) {
        if (status == Status.New) {
            arrayOf(again, hard, good).forEach { it.status = Status.Learning }
            easy.status = Status.Review
            again.lapses += 1
        } else if (status == Status.Learning || status == Status.Relearning) {
            arrayOf(again, hard).forEach { it.status = status }
            arrayOf(good, easy).forEach { it.status = Status.Review }
        } else if (status == Status.Review) {
            again.status = Status.Relearning
            arrayOf(hard, good, easy).forEach { it.status = Status.Review }
            again.lapses += 1
        }
    }

    fun schedule(now: LocalDateTime, hardInterval: Double, goodInterval: Double, easyInterval: Double) {
        again.scheduledDays = 0.0
        hard.scheduledDays = hardInterval
        good.scheduledDays = goodInterval
        easy.scheduledDays = easyInterval
        again.due = addTime(now, 5, DateTimeUnit.MINUTE)
        if (hardInterval > 0) {
            hard.due = addTime(now, hardInterval.toLong(), DateTimeUnit.DAY)
        } else {
            hard.due = addTime(now, 10, DateTimeUnit.MINUTE)
        }
        good.due = addTime(now, goodInterval.toLong(), DateTimeUnit.DAY)
        easy.due = addTime(now, easyInterval.toLong(), DateTimeUnit.DAY)
    }

    fun addTime(now: LocalDateTime, value: Long, unit: DateTimeUnit): LocalDateTime {
        var endTime = now.toInstant(TimeZone.currentSystemDefault())
        if (unit == DateTimeUnit.DAY) {
            endTime = endTime.plus(DateTimePeriod(days = value.toInt()), TimeZone.currentSystemDefault())
        } else if (unit == DateTimeUnit.MINUTE) {
            endTime = endTime.plus(DateTimePeriod(minutes = value.toInt()), TimeZone.currentSystemDefault())
        } else {
            throw Exception("Error: This function only supports days and minutes.")
        }
        return endTime.toLocalDateTime(TimeZone.currentSystemDefault())
    }

    fun recordLog(card: FSRSCard, now: LocalDateTime): Map<Rating, SchedulingInfo> {
        return mapOf(
            Rating.Again to SchedulingInfo(Rating.Again, again, card, now),
            Rating.Hard to SchedulingInfo(Rating.Hard, hard, card, now),
            Rating.Good to SchedulingInfo(Rating.Good, good, card, now),
            Rating.Easy to SchedulingInfo(Rating.Easy, easy, card, now)
        )
    }

    fun data(): Map<String, Map<String, Any>> {
        return mapOf(
            "again" to again.data(),
            "hard" to hard.data(),
            "good" to good.data(),
            "easy" to easy.data()
        )
    }
}

class Params// Initial Stability for Again
// Initial Stability for Hard
// Initial Stability for Good
// Initial Stability for Easy
    () {
    var requestRetention: Double
    var maximumInterval: Double
    var w: List<Double>

    init {
        this.requestRetention = 0.9
        this.maximumInterval = 36500.0
        this.w = listOf(
            0.4,  // Initial Stability for Again
            0.6,  // Initial Stability for Hard
            2.4,  // Initial Stability for Good
            5.8,  // Initial Stability for Easy
            4.93,
            0.94,
            0.86,
            0.01,
            1.49,
            0.14,
            0.94,
            2.18,
            0.05,
            0.34,
            1.26,
            0.29,
            2.61
        )
    }
}

class FSRS() {
    var p: Params

    init {
        this.p = Params()
    }

    // Was repeat
    fun repeat(card: FSRSCard, now: LocalDateTime): Map<Rating, SchedulingInfo> {
        val card = card.clone() as FSRSCard

        if (card.status == Status.New) {
            card.elapsedDays = 0.0
        } else {
            card.elapsedDays = max(0.0,
                (card.lastReview.toInstant(TimeZone.currentSystemDefault()).until(Clock.System.now(), DateTimeUnit.DAY, TimeZone.currentSystemDefault()).toDouble())
            )
        }

        println("Elapsed ${card.elapsedDays}")
        card.lastReview = now
        card.reps += 1

        val s = SchedulingCards(card)
        s.updateStatus(card.status)

        if (card.status == Status.New) {
            initDS(s)
            s.again.due = s.addTime(now, 1, DateTimeUnit.MINUTE)
            s.hard.due = s.addTime(now, 5, DateTimeUnit.MINUTE)
            s.good.due = s.addTime(now, 10, DateTimeUnit.MINUTE)
            val easyInterval = nextInterval(s.easy.stability)
            s.easy.scheduledDays = easyInterval
            s.easy.due = s.addTime(now, easyInterval.toLong(), DateTimeUnit.DAY)
        } else if (card.status == Status.Learning || card.status == Status.Relearning) {
            val hardInterval = 0.0
            val goodInterval = nextInterval(s.good.stability)
            val easyInterval = max(nextInterval(s.easy.stability), goodInterval + 1)
            s.schedule(now, hardInterval, goodInterval, easyInterval)
        } else if (card.status == Status.Review) {
            val interval = card.elapsedDays
            val lastDifficulty = card.difficulty
            val lastStability = card.stability
            val retrievability = (1 + interval / (9 * lastStability)).pow(-1.0)
            nextDS(s, lastDifficulty, lastStability, retrievability)
            var hardInterval = nextInterval(s.hard.stability)
            var goodInterval = nextInterval(s.good.stability)
            hardInterval = min(hardInterval, goodInterval)
            goodInterval = max(goodInterval, hardInterval + 1)
            val easyInterval = max(nextInterval(s.easy.stability), goodInterval + 1)
            s.schedule(now, hardInterval, goodInterval, easyInterval)
        }
        return s.recordLog(card, now)
    }

    fun initDS(s: SchedulingCards) {
        s.again.difficulty = initDifficulty(Rating.Again)
        s.again.stability = initStability(Rating.Again)
        s.hard.difficulty = initDifficulty(Rating.Hard)
        s.hard.stability = initStability(Rating.Hard)
        s.good.difficulty = initDifficulty(Rating.Good)
        s.good.stability = initStability(Rating.Good)
        s.easy.difficulty = initDifficulty(Rating.Easy)
        s.easy.stability = initStability(Rating.Easy)
    }

    fun nextDS(s: SchedulingCards, lastDifficulty: Double, lastStability: Double, retrievability: Double) {
        s.again.difficulty = nextDifficulty(lastDifficulty, Rating.Again)
        s.again.stability = nextForgetStability(s.again.difficulty, lastStability, retrievability)
        s.hard.difficulty = nextDifficulty(lastDifficulty, Rating.Hard)
        s.hard.stability = nextRecallStability(s.hard.difficulty, lastStability, retrievability, Rating.Hard)
        s.good.difficulty = nextDifficulty(lastDifficulty, Rating.Good)
        s.good.stability = nextRecallStability(s.good.difficulty, lastStability, retrievability, Rating.Good)
        s.easy.difficulty = nextDifficulty(lastDifficulty, Rating.Easy)
        s.easy.stability = nextRecallStability(s.easy.difficulty, lastStability, retrievability, Rating.Easy)
    }

    fun initStability(rating: Rating): Double {
        return initStability(rating.value())
    }

    fun initStability(r: Int): Double {
        return max(p.w[r - 1], 0.1)
    }

    fun initDifficulty(rating: Rating): Double {
        return initDifficulty(rating.value())
    }

    fun initDifficulty(r: Int): Double {
        return min(max(p.w[4] - p.w[5] * (r - 3), 1.0), 10.0)
    }

    fun nextInterval(s: Double): Double {
        val interval = s * 9 * (1 / p.requestRetention - 1)
        return min(max(round(interval), 1.0), p.maximumInterval)
    }

    fun nextDifficulty(d: Double, rating: Rating): Double {
        val r = rating.value()
        val nextD = d - p.w[6] * (r - 3)
        return min(max(meanReversion(p.w[4], nextD), 1.0), 10.0)
    }

    fun meanReversion(initial: Double, current: Double): Double {
        return p.w[7] * initial + (1 - p.w[7]) * current
    }

    fun nextRecallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) p.w[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) p.w[16] else 1.0
        return s * (1 + exp(p.w[8]) * (11 - d) * s.pow(-p.w[9]) * (exp((1 - r) * p.w[10]) - 1) * hardPenalty * easyBonus)
    }

    fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        return p.w[11] * d.pow(-p.w[12]) * ((s + 1.0).pow(p.w[13]) - 1) * exp((1 - r) * p.w[14])
    }
}