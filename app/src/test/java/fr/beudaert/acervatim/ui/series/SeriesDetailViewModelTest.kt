package fr.beudaert.acervatim.ui.series

import fr.beudaert.acervatim.domain.model.ItemStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesDetailViewModelTest {

    @Test
    fun `next status cycles OWNED to WISHLIST`() {
        assertEquals(ItemStatus.WISHLIST, nextStatusInCycle(ItemStatus.OWNED))
    }

    @Test
    fun `next status cycles WISHLIST to IGNORED`() {
        assertEquals(ItemStatus.IGNORED, nextStatusInCycle(ItemStatus.WISHLIST))
    }

    @Test
    fun `next status cycles IGNORED back to OWNED`() {
        assertEquals(ItemStatus.OWNED, nextStatusInCycle(ItemStatus.IGNORED))
    }

    @Test
    fun `full cycle returns to the original status`() {
        val start = ItemStatus.OWNED
        val end = nextStatusInCycle(nextStatusInCycle(nextStatusInCycle(start)))
        assertEquals(start, end)
    }
}
