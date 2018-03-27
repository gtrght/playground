package com.vvlasov.playground.rx;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.plugins.RxJavaPlugins;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Vasily Vlasov
 */
@Slf4j
public class ReactiveStreamsTest {
    @Test
    public void testSingle() throws Exception {
        Single<String> single = Single.just("Hello world");
        single.subscribe(new SingleObserver<String>() {
            @Override
            public void onSubscribe(Disposable d) {
                System.out.println("Sub");
            }

            @Override
            public void onSuccess(String s) {
                System.out.println("obSuc: " + s);
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("Error: " + e.getMessage());
            }
        });
    }

    @Test
    public void testFlowable() throws Exception {
        Flowable<Integer> flowable = Flowable.just("12", "123", "321")
                .map(Integer::parseInt)
                .filter(val -> val > 100);


        flowable.subscribe(new Subscriber<Integer>() {
            private Subscription subscribtion;

            @Override
            public void onSubscribe(Subscription s) {
                subscribtion = s;
                s.request(1);
                System.out.println("End subscription");
            }

            @Override
            @SneakyThrows
            public void onNext(Integer integer) {
                System.out.println(integer);
                Thread.sleep(300);
                subscribtion.request(1);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {
                System.out.println("Completed");
            }
        });

        System.out.println();
    }

    @Test
    public void operations() throws Exception {
        Flowable<Integer> vector1 = Flowable.just("12", "123", "321")
                .map(Integer::parseInt);
        Flowable<Integer> vector2 = Flowable.just("12", "123", "321")
                .map(Integer::parseInt);

        Flowable.zip(vector1, vector2, (v1, v2) -> v1 * v2).subscribe(System.out::println);
    }

    @Test
    public void subscriptionsWithFlowableSubscriber() throws Exception {
        Flowable<Integer> vector1 = Flowable.just("12", "123", "321")
                .map(Integer::parseInt);

        vector1.subscribe(new FlowableSubscriber<Integer>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
                System.out.println("End subscription");
            }

            @Override
            @SneakyThrows
            public void onNext(Integer integer) {
                System.out.println(integer);
                Thread.sleep(300);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Test
    public void backpressure1() throws Exception {
        Flowable<Integer> flowable = Flowable.create(new FlowableOnSubscribe<Integer>() {
            final AtomicInteger count = new AtomicInteger();

            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                while (!e.isCancelled()) {
                    e.onNext(count.incrementAndGet());
                }
            }
        }, BackpressureStrategy.DROP);

        flowable.subscribe(new Subscriber<Integer>() {
            private Subscription s;

            @Override
            public void onSubscribe(Subscription s) {
                this.s = s;
                s.request(100);
                new Thread() {
                    @Override
                    @SneakyThrows
                    public void run() {
                        sleep(1000);
                        s.request(100);
                        sleep(1000);
                        System.exit(1);
                    }
                }.start();
            }

            @Override
            public void onNext(Integer integer) {
                System.out.println(integer);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t);
            }

            @Override
            public void onComplete() {
                System.out.println("Completed");
            }
        });

    }

    @Test
    public void backpressure2() throws Exception {
        Flowable<Integer> flowable = Flowable.generate(() -> 0, new BiFunction<Integer, Emitter<Integer>, Integer>() {
            @Override
            public Integer apply(Integer integer, Emitter<Integer> integerEmitter) throws Exception {
                integerEmitter.onNext(integer);
                return integer + 1;
            }
        });

        flowable.subscribe(new FlowableSubscriber<Integer>() {
            @Override
            @SneakyThrows
            public void onSubscribe(Subscription s) {
                s.request(100);
                Thread.sleep(1000);
                s.request(100);
            }

            @Override
            public void onNext(Integer integer) {
                System.out.println(integer);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Test
    public void backpressure3() throws Exception {
        Iterator<Integer> iterator = IntStream.range(101, 251).iterator();

        Flowable<Integer> flowable = Flowable.generate(() -> iterator, (integerIterator, integerEmitter) -> {
            if (iterator.hasNext()) {
                integerEmitter.onNext(iterator.next());
            } else {
                integerEmitter.onComplete();
            }
            return iterator;
        });

        flowable.subscribe(new FlowableSubscriber<Integer>() {
            @Override
            @SneakyThrows
            public void onSubscribe(Subscription s) {
                s.request(100);
                Thread.sleep(1000);
                s.request(100);
            }

            @Override
            public void onNext(Integer integer) {
                System.out.println(integer);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Test
    public void usingFlowable() throws Exception {
        Flowable<String> flowable = Flowable.using(
                () -> new BufferedReader(new FileReader("/Users/vvlasov/Code/homework/playground/api-query/src/test/java/com/vvlasov/playground/rx/ReactiveStreamsTest.java")),
                reader -> Flowable.fromIterable(() -> reader.lines().iterator()),
                BufferedReader::close
        );

        FlowableSubscriber<String> subscriber = new FlowableSubscriber<String>() {
            private Subscription s;

            @Override
            @SneakyThrows
            public void onSubscribe(Subscription s) {
                ForkJoinPool.commonPool().submit(() -> {
                    while (true) {
                        Thread.sleep(200);
                        s.request(1);
                    }
                });
            }

            @Override
            public void onNext(String line) {
                System.out.println(line);
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onComplete() {
                System.out.println();
            }
        };
        flowable.subscribe(subscriber);

        Thread.sleep(20000);
    }

    @Test
    public void testInterval() throws Exception {
        Flowable.interval(3, 1, TimeUnit.SECONDS)
                .observeOn(RxJavaPlugins.createComputationScheduler(Thread::new))
                .subscribe(aLong -> System.out.println(aLong));

        Thread.sleep(10000);
    }

    @Test
    public void testIntervalSubscription() throws Exception {
        Flowable.interval(1, 1, TimeUnit.SECONDS)
                .observeOn(RxJavaPlugins.createComputationScheduler(Thread::new))
                .subscribe(new Subscriber<Long>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1);
                    }

                    @Override
                    public void onNext(Long aLong) {
                        System.out.println(aLong);
                    }

                    @Override
                    public void onError(Throwable t) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });

        Thread.sleep(5000);
    }

    @Test
    public void testIntervalTransformation() throws Exception {
        Iterable<Long> listSingle = Flowable.interval(1, 1, TimeUnit.SECONDS)
                .blockingIterable();

        List<Long> list = StreamSupport.stream(Spliterators.spliteratorUnknownSize(listSingle.iterator(), Spliterator.ORDERED),
                false).collect(Collectors.toList());
        System.out.printf("List: " + listSingle);

        Thread.sleep(10000);
    }


}
