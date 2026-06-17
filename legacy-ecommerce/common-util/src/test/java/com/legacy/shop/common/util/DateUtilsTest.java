package com.legacy.shop.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DateUtils 의 thread-safety(R3)와 파싱 오류 처리(C4)를 고정한다.
 *
 * - R3: 종전 static SimpleDateFormat 은 thread-unsafe 라 동시 호출 시 깨진 결과/예외가 났다.
 *   불변 DateTimeFormatter 로 교체했으므로 동시 format/parse 가 항상 정확해야 한다.
 * - C4: parse 는 형식 오류 시 null 을 삼키지 않고 DateTimeParseException(fail-fast)을 던진다.
 */
class DateUtilsTest {

    @Test
    void parse_thenFormat_roundTripsInDefaultZone() {
        String s = "2026-06-17 13:45:30";

        Date d = DateUtils.parse(s);

        assertThat(DateUtils.format(d)).isEqualTo(s);
    }

    @Test
    void parse_onInvalidFormat_throwsInsteadOfReturningNull() {
        // 종전: ParseException 을 삼키고 null 반환(C4). 이제: fail-fast 로 예외 전파.
        assertThatThrownBy(() -> DateUtils.parse("not-a-date"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void today_returnsIsoLocalDate() {
        assertThat(DateUtils.today()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void formatAndParse_areThreadSafeUnderConcurrency() throws Exception {
        // R3 회귀: 가변 포매터를 공유하면 동시 호출에서 깨진 문자열/예외가 난다.
        // 불변 DateTimeFormatter 라면 모든 스레드가 항상 정확한 결과를 얻어야 한다.
        String s = "2026-06-17 13:45:30";
        Date expected = DateUtils.parse(s);

        int threads = 16;
        int iterations = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                    .mapToObj(t -> (Callable<Boolean>) () -> {
                        for (int i = 0; i < iterations; i++) {
                            if (!s.equals(DateUtils.format(expected))) {
                                return false;
                            }
                            if (!expected.equals(DateUtils.parse(s))) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            List<Future<Boolean>> results = pool.invokeAll(tasks);
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue(); // 스레드 내 예외는 get() 에서 재발생한다
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
