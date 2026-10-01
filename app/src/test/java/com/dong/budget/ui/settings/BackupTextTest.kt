package com.dong.budget.ui.settings

import com.dong.budget.data.backup.Backup
import com.dong.budget.data.backup.BackupCategory
import com.dong.budget.data.backup.BackupKind
import com.dong.budget.data.backup.BackupPaymentMethod
import com.dong.budget.data.backup.BackupTransaction
import com.dong.budget.data.backup.LastBackup
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.salary.SalaryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupTextTest {
    private val today = LocalDate.of(2026, 9, 29)

    private fun backup(transactions: Int) = Backup(
        exportedAt = "2026-09-29T14:03:00+09:00",
        categories = listOf(BackupCategory("c1", CategoryScope.EXPENSE, "식비"), BackupCategory("c2", CategoryScope.INCOME, "급여")),
        paymentMethods = listOf(BackupPaymentMethod("p1", "현금", PaymentMethodType.CASH)),
        transactions =
        List(transactions) { i ->
            BackupTransaction("t$i", TransactionType.EXPENSE, 1_000, "2026-09-29T12:00:00+09:00", createdAt = "", updatedAt = "")
        },
    )

    @Test
    fun `마지막 백업은 서울 날짜로 며칠 전인지와 방법을 적는다`() {
        assertEquals("아직 백업한 적 없어요", lastBackupText(null, today))
        // 9월 29일 오전 8시(서울)
        val morning = Instant.parse("2026-09-28T23:00:00Z")
        assertEquals("마지막 백업 · 오늘 · 파일로 저장", lastBackupText(LastBackup(morning, BackupKind.FILE), today))
        // 9월 28일 오후 11시(서울)는 UTC 로는 같은 날이지만 서울로는 어제다
        val lateNight = Instant.parse("2026-09-28T14:00:00Z")
        assertEquals("마지막 백업 · 어제 · JSON 복사", lastBackupText(LastBackup(lateNight, BackupKind.COPY), today))
        val longAgo = Instant.parse("2026-08-01T03:00:00Z")
        assertEquals("마지막 백업 · 59일 전 · 자동 백업", lastBackupText(LastBackup(longAgo, BackupKind.AUTO), today))
    }

    @Test
    fun `파일 이름은 날짜를 붙인다`() {
        assertEquals("동계부-백업-2026-09-29.json", backupFileName(today))
    }

    @Test
    fun `복원 확인 글은 무엇을 담았고 지금 무엇이 지워지는지 알린다`() {
        val preview = RestorePreview(backup(1_234), Instant.parse("2026-09-29T05:03:00Z"), currentTransactions = 60)
        assertEquals(
            "9월 29일 (화) 오후 2:03에 만든 백업이에요.\n거래 1,234건 · 분류 2개 · 결제수단 1개\n\n" +
                "지금 있는 거래 60건과 분류·결제수단은 모두 지우고 백업 내용으로 바꿔요. 되돌릴 수 없어요.",
            restoreMessage(preview, today),
        )
    }

    @Test
    fun `지금 거래가 없으면 거래 0건이라고 적지 않는다`() {
        val preview = RestorePreview(backup(3), Instant.parse("2025-12-31T15:00:00Z"), currentTransactions = 0)
        assertEquals(
            "2026년 1월 1일 (목) 오전 12:00에 만든 백업이에요.\n거래 3건 · 분류 2개 · 결제수단 1개\n\n" +
                "지금 있는 분류·결제수단은 모두 지우고 백업 내용으로 바꿔요. 되돌릴 수 없어요.",
            restoreMessage(preview, LocalDate.of(2027, 1, 2)),
        )
    }

    @Test
    fun `데이터 초기화 글은 지워지는 거래 수와 남는 것을 알린다`() {
        assertEquals(
            "거래 1,500건과 직접 만든 분류·결제수단, 월급 설정, 알림 목록을 모두 지우고 처음 설치한 상태로 돌려요. " +
                "되돌릴 수 없으니 먼저 설정의 '파일로 내려받기'로 백업해 두세요.\n\n화면 테마와 자동 기능 설정은 그대로예요.",
            resetDataMessage(1_500),
        )
    }

    @Test
    fun `월급 설정이 담긴 백업이면 복원 글에 알린다`() {
        val preview = RestorePreview(backup(1).copy(salary = SalaryRecord(amount = 3_000_000)), Instant.parse("2026-09-29T05:03:00Z"), 0)
        assertTrue(restoreMessage(preview, today).contains("결제수단 1개 · 월급 설정\n"))
    }
}
