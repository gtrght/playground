package com.apple.amp.perf

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.selects.select
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.system.measureTimeMillis

/**
 * @author Vasily Vlasov
 */
fun main(args: Array<String>) = runBlocking {
    //    printIntegers(100_000)
//    printHello()
//    asDaemonThread()
//    cancellation()
//    cancellationCooperative()
//    cancellationNonCancellable()
//    cancellationByTimeout()
//    runSequentialTasks()
//    coroutineContextExample(coroutineContext)
//    coroutineDebug()
//    coroutineWithContext()
//    childJob()
//    combineCoroutineContexts(coroutineContext)
//    parentCoroutineResponsibility()
//    explicitLifeCycleManagement(coroutineContext)
//    usingChannels1()
//    usingChannels2()
//    produceConsume()
//    pipeline(coroutineContext)
//    fanOut()
//    fanIn()
//    bufferedChannels()
//    channelsAreFair()
//    concurrencyProblem()
//    actorsInAction()
    selectFromMultipleChannels()
}


suspend fun selectFromMultipleChannels() {
    fun fizz(context: CoroutineContext) = produce<String>(context) {
        while (true) { // sends "Fizz" every 300 ms
            delay(300)
            send("Fizz")
        }
    }

    fun buzz(context: CoroutineContext) = produce<String>(context) {
        while (true) { // sends "Buzz!" every 500 ms
            delay(500)
            send("Buzz!")
        }
    }

    fun selectFizzBuzz(fizz: ReceiveChannel<String>, buzz: ReceiveChannel<String>) = runBlocking {
        return@runBlocking select<String> {
            // <Unit> means that this select expression does not produce any result
            fizz.onReceiveOrNull { value ->
                // this is the first select clause
                if (value == null)
                    "Channel 'a' is closed"
                else
                    "a -> '$value'"
            }
            buzz.onReceiveOrNull { value ->
                if (value == null)
                    "Channel 'b' is closed"
                else
                    "b -> '$value'"
            }
        }
    }

    val fizz = fizz(CommonPool)
    val buzz = buzz(CommonPool)
    for (i in 1..7) {
        selectFizzBuzz(fizz, buzz)
    }
    CommonPool.cancelChildren() // cancel fizz & buzz coroutines
}


sealed class CounterMsg
object IncCounter : CounterMsg()
class GetCounter(val response: CompletableDeferred<Int>) : CounterMsg()

suspend fun actorsInAction() {
    fun counterActor() = actor<CounterMsg> {
        var counter = 0 // actor state
        for (msg in channel) { // iterate over incoming messages
            when (msg) {
                is IncCounter -> counter++
                is GetCounter -> msg.response.complete(counter)
            }
        }
    }

    val counter = counterActor()
    massiveRun0(CommonPool) {
        counter.send(IncCounter)
    }

    val response = CompletableDeferred<Int>()
    counter.send(GetCounter(response))

    println("Counter = ${response.await()}")
    counter.close()
}

suspend fun concurrencyProblem() {
    val mutex = Mutex()
    var counter = 0
//    var atomicCounter = AtomicInteger()
    newFixedThreadPoolContext(4, "mtPool").use {
        massiveRun0(it) {
            mutex.withLock {
                counter++
            }
//            atomicCounter.incrementAndGet()
        }
    }

    println("Counter = $counter")
}

suspend fun massiveRun0(context: CoroutineContext, action: suspend () -> Unit) {
    val n = 100 // number of coroutines to launch
    val k = 100 // times an action is repeated by each coroutine
    val time = measureTimeMillis {
        val jobs = List(n) {
            launch(context) {
                repeat(k) { action() }
            }
        }
        jobs.forEach { it.join() }
    }
    println("Completed ${n * k} actions in $time ms")
}

suspend fun channelsAreFair() {
    data class Ball(var hits: Int)

    fun player(name: String, table: Channel<Ball>) {
        launch(CommonPool) {
            for (ball in table) { // receive the ball in a loop
                ball.hits++
                println("$name $ball")
                delay(300) // wait a bit
                table.send(ball) // send the ball back
            }
        }
    }

    val table = Channel<Ball>() // a shared table
    launch(CommonPool) { player("ping", table) }
    launch(CommonPool) { player("pong", table) }
    table.send(Ball(0)) // serve the ball
    delay(1000) // delay 1 second
    CommonPool.cancelChildren() // game over, cancel them
}

suspend fun bufferedChannels() {
    val channel = Channel<Int>(4) // create buffered channel
    val sender = launch(CommonPool) {
        // launch sender coroutine
        repeat(10) {
            println("Sending $it") // print before sending each element
            channel.send(it) // will suspend when buffer is full
        }
    }
    // don't receive anything... just wait....
    delay(1000)
    sender.cancel() // cancel sender coroutine
}

suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
    while (true) {
        delay(time)
        channel.send(s)
    }
}

suspend fun fanIn() {
    val channel = Channel<String>()
    launch(CommonPool) { sendString(channel, "foo", 200L) }
    launch(CommonPool) { sendString(channel, "BAR!", 500L) }
    repeat(20) {
        // receive first six
        println(channel.receive())
    }
    CommonPool.cancelChildren() // cancel all children to let main finish
}

suspend fun fanOut() {
    fun produceNumbers() = produce<Int> {
        var x = 1 // start from 1
        while (true) {
            send(x++) // produce next
            delay(100) // wait 0.1s
        }
    }

    fun launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
        channel.consumeEach {
            println("Processor #$id received $it")
        }
    }

    val producer = produceNumbers()
    repeat(5) { launchProcessor(it, producer) }
    delay(950)
    producer.cancel() // cancel producer coroutine and thus kill them all
}

suspend fun pipeline(ctx: CoroutineContext) {
    fun numbersFrom(context: CoroutineContext, start: Int) = produce<Int>(context) {
        var x = start
        while (true) send(x++) // infinite stream of integers from start
    }

    fun oddNumber(context: CoroutineContext, numbers: ReceiveChannel<Int>) = produce<Int>(context) {
        for (x in numbers) if (x % 2 == 1) send(x)
    }

    fun produceNumbers() = produce<Int> {
        var x = 1
        while (true) send(x++) // infinite stream of integers starting from 1
    }

    fun square(numbers: ReceiveChannel<Int>) = produce<Int> {
        for (x in numbers) send(x * x)
    }

    val numbers = produceNumbers() // produces integers from 1 and on
    val squares = square(numbers) // squares integers
    for (i in 1..10) println(squares.receive()) // print first five
    println("Done!") // we are done
    squares.cancel() // need to cancel these coroutines in a larger app
    numbers.cancel()

    val oddNumbers = oddNumber(ctx, numbersFrom(ctx, 2))
    for (i in 1..10) println(oddNumbers.receive()) // print first five
    ctx.cancelChildren()
}

suspend fun produceConsume() {
    fun produceSquares() = produce<Int> {
        for (x in 1..5) send(x * x)
    }

    val squares = produceSquares()
//    squares.cancel()
    squares.consumeEach { println(it) }
    println("Done!")
}

suspend fun usingChannels2() {
    val channel = Channel<Int>()
    launch {
        for (x in 1..5) channel.send(x * x)
        channel.close() // we're done sending
    }
    // here we print received values using `for` loop (until the channel is closed)
    for (y in channel) println(y)
    println("Done!")
}

suspend fun usingChannels1() {
    val channel = Channel<Int>()

    launch {
        // this might be heavy CPU-consuming computation or async logic, we'll just receive squares
        for (x in 1..5) println(channel.receive())
    }

    launch {
        // this might be heavy CPU-consuming computation or async logic, we'll just send five squares
        for (x in 1..5) channel.send(x * x)
    }.join()
    // here we print five received integers:
    println("Done!")
}

suspend fun explicitLifeCycleManagement(ctx: CoroutineContext) {
    val job = Job() // create a job object to manage our lifecycle
    // now launch ten coroutines for a demo, each working for a different time
    val coroutines = List(10) { i ->
        // they are all children of our job object
        launch(ctx, parent = job) {
            // we use the context of main runBlocking thread, but with our parent job
            delay((i + 1) * 200L) // variable delay 200ms, 400ms, ... etc
            println("Coroutine $i is done")
        }
    }
    println("Launched ${coroutines.size} coroutines")
    delay(1000L) // delay for half a second
    println("Cancelling the job!")
    job.cancelAndJoin() // cancel all our coroutines and wait for all of them to complete
}

suspend fun parentCoroutineResponsibility() {
    // launch a coroutine to process some kind of incoming request
    val request = launch(CoroutineName("my-coroutine")) {
        repeat(3) { i ->
            // launch a few children jobs
            launch(coroutineContext.plus(CoroutineName("my-coroutine-$i"))) {
                delay((i + 1) * 200L) // variable delay 200ms, 400ms, 600ms
                println("[${Thread.currentThread().name}] Coroutine is done")
            }
        }
        println("request: I'm done and I don't explicitly join my children that are still active")
    }
    request.join() // wait for completion of the request, including all its children
    println("Now processing of the request is complete")
}

suspend fun combineCoroutineContexts(ctx: CoroutineContext) {
    // start a coroutine to process some kind of incoming request
    val request = launch(ctx) {
        // use the context of `runBlocking`
        // spawns CPU-intensive child job in CommonPool !!!
        val job = launch(coroutineContext + CommonPool) {
            println("job: I am a child of the request coroutine, but with a different dispatcher")
            delay(2000)
            println("job: I will not execute this line if my parent request is cancelled")
        }
        job.join() // request completes when its sub-job completes
    }
    delay(500)
    request.cancel() // cancel processing of the request
    delay(1000) // delay a second to see what happens
    println("main: Who has survived request cancellation?")

}

suspend fun childJob() {
    // launch a coroutine to process some kind of incoming request
    val request = launch {
        // it spawns two other jobs, one with its separate context
        val job1 = launch {
            println("[${Thread.currentThread().name}] job1: I have my own context and execute independently!")
            delay(1000)
            println("[${Thread.currentThread().name}] job1: I am not affected by cancellation of the request")
        }
        // and the other inherits the parent context
        val job2 = launch(coroutineContext) {
            delay(100)
            println("[${Thread.currentThread().name}] job2: I am a child of the request coroutine")
            delay(1000)
            println("[${Thread.currentThread().name}] job2: I will not execute this line if my parent request is cancelled")
        }
        // request completes when both its sub-jobs complete:
        job1.join()
        job2.join()
    }
    delay(500)
    request.cancel() // cancel processing of the request
    delay(1000) // delay a second to see what happens
    println("[${Thread.currentThread().name}] main: Who has survived request cancellation?")
}

suspend fun coroutineWithContext() {
    newSingleThreadContext("Ctx1").use { ctx1 ->
        newSingleThreadContext("Ctx2").use { ctx2 ->
            runBlocking(ctx1) {
                log("Started in ctx1")
                withContext(ctx2) {
                    log("Working on ${coroutineContext[Job]!!.isActive}")
                }
                log("Back to ctx1")
            }
        }
    }
}

fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

suspend fun coroutineDebug() {
    val a = async(Unconfined) {
        val b = async(Unconfined) {
            log("I'm computing another piece of the answer")
            7
        }
        log("I'm computing a piece of the answer")
        6 + b.await()
    }
    val b = async(Unconfined) {
        log("I'm computing another piece of the answer")
        7
    }
    log("The answer is ${a.await() * b.await()}")
}


suspend fun coroutineContextExample(coroutineContext: CoroutineContext) {
    val jobs = arrayListOf<Job>()
    jobs += launch(Unconfined) {
        // not confined -- will work with main thread
        println("      'Unconfined': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs += launch(coroutineContext) {
        // context of the parent, runBlocking coroutine
        println("'coroutineContext': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs += launch {
        // context of the parent, runBlocking coroutine
        println("         'default': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs += launch(CommonPool) {
        // will get dispatched to ForkJoinPool.commonPool (or equivalent)
        println("      'CommonPool': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs += launch(newSingleThreadContext("MyOwnThread")) {
        // will get its own new thread
        println("          'newSTC': I'm working in thread ${Thread.currentThread().name}")
    }
    jobs.forEach { it.join() }

}

suspend fun runSequentialTasks() {
    val time = measureTimeMillis {
        val one = async { sequential1() }
        val two = async(start = CoroutineStart.LAZY) { sequential2() }
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}

suspend fun sequential1(): Int {
    delay(1000)
    return 1
}

suspend fun sequential2(): Int {
    delay(1000)
    return 2
}

suspend fun cancellationByTimeout() {
    val value = withTimeoutOrNull(1300L, TimeUnit.MILLISECONDS) {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
        "Result"
    }
    println("The result is $value")
}

suspend fun cancellationNonCancellable() {
    val job = launch {
        try {
            repeat(1000) { i ->
                println("I'm sleeping $i ...")
                delay(500L)
            }
        } finally {
            withContext(NonCancellable) {
                println("I'm running finally")
                delay(1000L)
                println("And I've just delayed for 1 sec because I'm non-cancellable")
            }
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}

suspend fun cancellationCooperative() {
    val startTime = System.currentTimeMillis()
    val job = launch {
        var nextPrintTime = startTime
        var i = 0
        while (isActive) { // computation loop, just wastes CPU
//        while (i < 5) { // computation loop, just wastes CPU
            // print a message twice a second
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("I'm sleeping ${i++} ...")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancelAndJoin() // cancels the job and waits for its completion
    println("main: Now I can quit.")
}

suspend fun cancellation() {
    val job = launch {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    }
    delay(1300L) // delay a bit
    println("main: I'm tired of waiting!")
    job.cancel() // cancels the job
    job.join() // waits for job's completion


    delay(2000L) // delay a bit
    println("main: Now I can quit.")
}


private suspend fun asDaemonThread() {
    launch {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    }
    delay(1300L) // just quit after delay
}

private suspend fun printIntegers(size: Int) {
    val jobs = List(size, {
        // launch a lot of coroutines and list their jobs
        launch {
            delay(1000L)
            print(".")
        }
    })
    jobs.forEach { it.join() } // wait for all jobs to complete
}

private suspend fun printHello() {
    var job = launch {

        printWithWaiting() // print after delay
    }
    println("Hello,") // main thread continues while coroutine is delayed
    job.join()
    println("Finished") // print after delay
}

// launch new coroutine in background and continue
private suspend fun printWithWaiting() {
    delay(1000L) // non-blocking delay for 1 second (default time unit is ms)
    println("World!")
}