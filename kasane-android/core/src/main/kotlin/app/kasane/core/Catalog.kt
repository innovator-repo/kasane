package app.kasane.core

/**
 * A reference list of routines, never added to a journal automatically: a new
 * install starts empty. The starting list, taken from the routines in Samsung Reminders and the
 * Habits app. Reminder times avoid the 22:00-08:00 sleep lock.
 */
object Catalog {
    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    const val RELAPSE_ID = "no_shit"

    val defaults: List<Item> = listOf(
        // Morning
        Item("sleep_ok", "Proper sleep last night", Kind.CHECK, Block.MORNING),
        Item("yoga", "Morning yoga", Kind.CHECK, Block.MORNING, remindAt = at(8, 15)),
        Item("exercise", "Exercise", Kind.CHECK, Block.MORNING, remindAt = at(8, 30)),
        Item("squats", "Squats", Kind.COUNT, Block.MORNING, target = 50),
        Item("pushups", "Pushups", Kind.COUNT, Block.MORNING, target = 30),
        Item("jacks", "Jumping jacks", Kind.COUNT, Block.MORNING, target = 50),
        Item("office_site", "Office website: anything new assigned", Kind.CHECK, Block.MORNING,
            days = Days.OFFICE_DAYS, remindAt = at(9, 30)),
        Item("office_app", "Opened the office application", Kind.CHECK, Block.MORNING,
            days = Days.OFFICE_DAYS),

        // Day
        Item("reading", "Read The Compound Effect", Kind.CHECK, Block.DAY),
        Item("maths", "Maths practice", Kind.TIMED, Block.DAY, target = 20, remindAt = at(18, 0)),
        Item("meditation", "Meditation", Kind.TIMED, Block.DAY, target = 10),
        Item("monkeytype", "Monkeytype", Kind.CHECK, Block.DAY, opens = "https://monkeytype.com"),
        Item("duolingo", "Duolingo Japanese lesson", Kind.CHECK, Block.DAY,
            remindAt = at(19, 0), opens = "com.duolingo"),
        Item("ren", "Ren session", Kind.CHECK, Block.DAY, remindAt = at(19, 15), opens = "app.ren.japanese"),
        Item("sudoku", "1 Sudoku", Kind.CHECK, Block.DAY),
        Item("puzzles", "Pattern puzzles", Kind.COUNT, Block.DAY, target = 5),

        // Night (all before the 22:00 lock)
        Item("diary", "Daily diary", Kind.CHECK, Block.NIGHT, remindAt = at(21, 15)),
        Item("protein", "Enough protein today", Kind.CHECK, Block.NIGHT),
        Item("meditation_night", "Meditation before sleep", Kind.TIMED, Block.NIGHT, target = 5,
            remindAt = at(21, 30)),
        Item("clipboard", "Clipboard history cleared", Kind.CHECK, Block.NIGHT, remindAt = at(21, 45)),

        // Rules for the whole day
        Item(RELAPSE_ID, "Didn't watch shit", Kind.RULE, Block.ALL_DAY, isRelapse = true),
        Item("no_new_content", "No new anime or movies", Kind.RULE, Block.ALL_DAY),
        Item("lunch_after_12", "Lunch only after 12 pm", Kind.RULE, Block.ALL_DAY),
        Item("no_noon_sleep", "No sleeping at noon", Kind.RULE, Block.ALL_DAY),
        Item("no_sweets", "No excessive sweets", Kind.RULE, Block.ALL_DAY),
        Item("no_processed", "Not too much processed food", Kind.RULE, Block.ALL_DAY),
        Item("no_overeating", "No overeating", Kind.RULE, Block.ALL_DAY),
        Item("no_procrastination", "No procrastination", Kind.RULE, Block.ALL_DAY),
        Item("no_complaints", "No complaints about others", Kind.RULE, Block.ALL_DAY),
        Item("chrome_only", "Only Chrome as the browser", Kind.RULE, Block.ALL_DAY),
        Item("no_chia", "No chia seeds", Kind.RULE, Block.ALL_DAY, paused = true),
    )
}
