package com.pylo.yoinker.download

import com.pylo.yoinker.core.Fmt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue is a singleton shared by the service, the share sheet and the routines,
 * so each test clears up after itself rather than assuming it ran first.
 */
class QueueTest {

    @After
    fun tearDown() {
        Queue.jobs.value.forEach { Queue.remove(it.id) }
        Queue.setPaused(false)
    }

    private fun job(url: String = "https://example.com/a") =
        YoinkJob(url = url, format = Fmt.MP3, quality = "192K")

    @Test
    fun `works jobs in the order they arrived`() {
        val first = Queue.add(job("https://example.com/1"))
        Queue.add(job("https://example.com/2"))
        assertEquals(first.id, Queue.nextQueued()?.id)
    }

    @Test
    fun `a running job is not handed out again`() {
        val a = Queue.add(job("https://example.com/1"))
        val b = Queue.add(job("https://example.com/2"))
        Queue.update(a.id) { it.copy(state = JobState.RUNNING) }
        assertEquals(b.id, Queue.nextQueued()?.id)
        assertEquals(a.id, Queue.running()?.id)
    }

    @Test
    fun `finished jobs stop counting as pending`() {
        val a = Queue.add(job("https://example.com/1"))
        val b = Queue.add(job("https://example.com/2"))
        assertEquals(2, Queue.pendingCount())

        Queue.update(a.id) { it.copy(state = JobState.DONE) }
        Queue.update(b.id) { it.copy(state = JobState.FAILED) }
        assertEquals(0, Queue.pendingCount())
        assertNull(Queue.nextQueued())
    }

    @Test
    fun `retry puts a failed job back in line and clears the old error`() {
        val a = Queue.add(job())
        Queue.update(a.id) { it.copy(state = JobState.FAILED, error = "boom", progress = 40f) }

        Queue.retry(a.id)

        val again = Queue.get(a.id)!!
        assertEquals(JobState.QUEUED, again.state)
        assertNull(again.error)
        assertEquals(0f, again.progress, 0.001f)
    }

    @Test
    fun `clearing finished keeps the work still to do`() {
        val done = Queue.add(job("https://example.com/done"))
        val waiting = Queue.add(job("https://example.com/waiting"))
        Queue.update(done.id) { it.copy(state = JobState.DONE) }

        Queue.clearFinished()

        assertEquals(listOf(waiting.id), Queue.jobs.value.map { it.id })
    }

    @Test
    fun `a job knows when it is over`() {
        val a = Queue.add(job())
        assertTrue(!Queue.get(a.id)!!.isFinished)

        listOf(JobState.DONE, JobState.FAILED, JobState.CANCELED).forEach { state ->
            Queue.update(a.id) { it.copy(state = state) }
            assertTrue("$state should count as finished", Queue.get(a.id)!!.isFinished)
        }
    }

    @Test
    fun `a job with no title falls back to something showable`() {
        val a = Queue.add(job("https://example.com/clip"))
        assertEquals("https://example.com/clip", Queue.get(a.id)!!.label)

        Queue.update(a.id) { it.copy(title = "A real title") }
        assertEquals("A real title", Queue.get(a.id)!!.label)
    }
}
