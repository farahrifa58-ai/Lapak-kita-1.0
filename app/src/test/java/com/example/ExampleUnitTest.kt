package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testAdminGrossProfitCalculation() {
        val sellingPrice = 15000.0
        val sellerShare = 12000.0
        val quantity = 10
        val profitPerUnit = sellingPrice - sellerShare
        val totalAdminGrossProfit = quantity * profitPerUnit

        assertEquals(3000.0, profitPerUnit, 0.001)
        assertEquals(30000.0, totalAdminGrossProfit, 0.001)
    }

    @Test
    fun testAdminNetProfitCalculation() {
        val grossProfit = 150000.0
        val operatingExpenses = 25000.0
        val netProfit = grossProfit - operatingExpenses

        assertEquals(125000.0, netProfit, 0.001)
    }

    @Test
    fun testSellerRightsCalculation() {
        val sellerSharePerUnit = 11000.0
        val soldQty = 12
        val totalSellerRights = sellerSharePerUnit * soldQty
        val amountPaid = 100000.0
        val remainingUnpaid = totalSellerRights - amountPaid

        assertEquals(132000.0, totalSellerRights, 0.001)
        assertEquals(32000.0, remainingUnpaid, 0.001)
    }
}
