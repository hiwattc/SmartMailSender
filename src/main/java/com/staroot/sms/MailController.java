package com.staroot.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

//@Slf4j
@Controller
@RequiredArgsConstructor
public class MailController {
    private final MailService mailService;
    private static final Logger log = LoggerFactory.getLogger(MailController.class);
    @GetMapping("/")
    public String showMailForm() {
        return "mail_form";
    }

    @PostMapping("/preview")
    @ResponseBody
    public ResponseEntity<String> previewMail(@RequestBody Map<String, String> request) {
        log.info("메일 미리보기 요청 수신: {}", request);
        String template = request.get("template");
        Map<String, String> sampleData = request.get("customData") != null ?
                parseCSVLine(request.get("customData").split("\n")[0],
                        request.get("headers").split(",")) :
                new HashMap<>();

        for (Map.Entry<String, String> entry : sampleData.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        log.info("메일 미리보기 생성 완료: {}", template);
        return ResponseEntity.ok(template);
    }

    @PostMapping("/send")
    public SseEmitter sendMail(@RequestBody Map<String, String> request) {
        log.info("메일 발송 요청 수신");
        log.debug("요청 데이터: {}", request);

        //SseEmitter emitter = new SseEmitter();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // timeout 증가

        try {
            String[] headers = request.get("headers").split(",");
            String emailColumn = request.get("emailColumn");
            String[] csvData = request.get("csvData").split("\n");
            String subject = request.get("subject");
            String template = request.get("template");

            log.info("헤더 정보: {}", Arrays.toString(headers));
            log.info("이메일 컬럼: {}", emailColumn);
            log.info("총 수신자 수: {}", csvData.length);
            log.info("메일 제목: {}", subject);
            log.debug("메일 템플릿: {}", template);

            List<RecipientDTO> recipients = Arrays.stream(csvData)
                    .map(line -> {
                        RecipientDTO dto = createRecipientDTO(line, headers, emailColumn);
                        log.debug("수신자 생성: {}", dto);
                        return dto;
                    })
                    .collect(Collectors.toList());

            int total = recipients.size();
            AtomicInteger current = new AtomicInteger(0);
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);

            CompletableFuture.allOf(
                    recipients.stream()
                            .map(recipient -> mailService.sendEmail(recipient, subject, template)
                                    .thenAccept(result -> {
                                        try {
                                            if (result.isSuccess()) {
                                                log.info("메일 발송 성공 - 수신자: {}", result.getEmail());
                                                success.incrementAndGet();
                                            } else {
                                                log.error("메일 발송 실패 - 수신자: {}, 에러: {}",
                                                        result.getEmail(), result.getErrorMessage());
                                                failed.incrementAndGet();
                                            }
                                            Map<String, Object> progress = Map.of(
                                                    "current", current.incrementAndGet(),
                                                    "total", total,
                                                    "success", success.get(),
                                                    "failed", failed.get()
                                            );
                                            log.debug("진행 상황: {}", progress);
                                            emitter.send(progress);
                                        } catch (Exception e) {
                                            log.error("진행 상황 전송 중 오류 발생", e);
                                            emitter.completeWithError(e);
                                        }
                                    }))
                            .toArray(CompletableFuture[]::new)
            ).thenRun(() -> {
                log.info("모든 메일 발송 완료 - 성공: {}, 실패: {}", success.get(), failed.get());
                emitter.complete();
            });

        } catch (Exception e) {
            log.error("메일 발송 처리 중 예외 발생", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private RecipientDTO createRecipientDTO(String csvLine, String[] headers, String emailColumn) {
        Map<String, String> data = parseCSVLine(csvLine, headers);
        RecipientDTO dto = RecipientDTO.builder()
                .email(data.get(emailColumn))
                .customData(data)
                .build();
        log.debug("RecipientDTO 생성: {}", dto);
        return dto;
    }

    private Map<String, String> parseCSVLine(String line, String[] headers) {
        String[] values = line.split(",");
        Map<String, String> data = new HashMap<>();
        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
            data.put(headers[i].trim(), values[i].trim());
        }
        log.debug("CSV 라인 파싱 결과: {}", data);
        return data;
    }
}