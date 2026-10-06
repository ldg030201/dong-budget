package com.dong.budget.data.settings

/**
 * 앱이 알아서 골라 주거나 채워 주는 기능들. 설정에서 하나씩 켜고 끈다. 처음에는 모두 켜져 있다.
 *
 * @property key 설정 저장소의 열쇠. 저장된 값을 가리키므로 바꾸지 않는다(바꾸면 사용자가 끈 스위치가 다시 켜진다).
 * @property parent 이 기능이 기대는 스위치. 그 스위치가 꺼져 있으면 이것도 동작하지 않는다(설정에서도 흐리게 막힌다).
 */
enum class AutoOption(val key: String, val parent: AutoOption? = null) {
    /** 토스 결제 알림이 오면 '가계부에 등록할까요?' 알림으로 묻는다 */
    CAPTURE_PROMPT("auto_capture_prompt"),

    /** 토스가 결제 한 건을 알림 두 개로 보내면(금액이 같고 3초 안) 먼저 온 것만 묻는다 */
    CAPTURE_DEDUPE("auto_capture_dedupe", parent = CAPTURE_PROMPT),

    /** 앱을 열 때마다 알림창에 남은 토스 알림을 다시 살펴 묻지 못한 결제를 묻는다 */
    CAPTURE_RESCAN("auto_capture_rescan", parent = CAPTURE_PROMPT),

    /** 알림으로 연 등록창에서 같은 가게로 전에 등록한 지출의 분류를 미리 고른다 */
    FILL_CATEGORY("auto_fill_category"),

    /** 알림으로 연 등록창에서 알림의 카드 이름과 같은 결제수단을 미리 고른다 */
    FILL_PAYMENT("auto_fill_payment"),

    /** 알림의 카드와 같은 결제수단이 없으면 '신규' 로 골라 두고 저장할 때 새로 만든다 */
    FILL_NEW_CARD("auto_fill_new_card", parent = FILL_PAYMENT),

    /** 알림의 할부 표시를 메모에 'N개월 할부' 로 채운다 */
    FILL_INSTALLMENT("auto_fill_installment"),

    /** 새로 등록할 때 금액 키패드를 열어 둔 채 시작한다 */
    EDITOR_KEYPAD("auto_editor_keypad"),

    /** 등록창에서 분류·결제수단을 새로 만들면 바로 그것을 고른다 */
    EDITOR_SELECT_ADDED("auto_editor_select_added"),

    /** 분류·결제수단을 새로 만들 때 아직 안 쓴 색을 미리 고른다. 끄면 회색으로 시작한다. */
    NEW_ITEM_COLOR("auto_new_item_color"),

    /** 앱을 열 때 새 버전을 확인하고, 있으면 홈에 알림 줄을 띄운다 */
    UPDATE_CHECK("auto_update_check"),

    /** 복원·데이터 초기화로 지금 데이터를 지우기 전에 다운로드 폴더에 백업 파일로 저장한다. 저장하지 못하면 지우지 않는다. */
    BACKUP_BEFORE_REPLACE("auto_backup_before_replace"),

    /** 통계 일별에서 처음 볼 날을 고른다(이번 달은 오늘, 지나간 달은 가장 많이 쓴 날). 끄면 1일부터 본다. */
    STATS_DAY("auto_stats_day"),
}

/**
 * 자동 기능을 어떻게 켜고 껐는지. 저장소에는 끈 것만 적어서, 적힌 적 없는 기능(새로 생긴 것 포함)은 켜진 것이다.
 * @property off 사용자가 끈 스위치들
 */
data class AutoSettings(val off: Set<AutoOption> = emptySet()) {
    /** 스위치에 보이는 값. 사용자가 고른 그대로다. */
    fun chosen(option: AutoOption): Boolean = option !in off

    /** 실제로 동작하는지. 스위치가 켜져 있어도 기대는 스위치([AutoOption.parent])가 꺼져 있으면 동작하지 않는다. */
    operator fun get(option: AutoOption): Boolean = chosen(option) && option.parent?.let(::get) != false

    /** [option] 스위치만 바꾼 설정 */
    fun with(option: AutoOption, on: Boolean): AutoSettings = AutoSettings(if (on) off - option else off + option)
}
