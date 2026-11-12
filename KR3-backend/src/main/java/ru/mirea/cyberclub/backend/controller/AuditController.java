package ru.mirea.cyberclub.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.mirea.cyberclub.backend.dto.AuditDtos.AuditResponse;
import ru.mirea.cyberclub.backend.dto.PageResponse;
import ru.mirea.cyberclub.backend.service.AuditService;
import ru.mirea.cyberclub.backend.web.PageableFactory;

/** Журнал аудита. Доступно только роли ADMIN (правило /api/admin/** в SecurityConfig). */
@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Журнал аудита (администратор)", description = "Входы и изменения данных: кто, что, когда")
public class AuditController {
    private final AuditService audit;
    private final PageableFactory pageableFactory;

    public AuditController(AuditService audit, PageableFactory pageableFactory) {
        this.audit = audit;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    @Operation(summary = "Поиск по журналу с сортировкой и пагинацией",
            description = "q — подстрока типа события, email, описания, идентификатора записи или correlation ID; "
                    + "type — точный тип события (LOGIN_SUCCESS, BOOKING_CREATED, ...); "
                    + "sort: occurred|type|actor|entity, через запятую asc|desc.")
    public PageResponse<AuditResponse> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "25") int size,
                                              @RequestParam(defaultValue = "occurred,desc") String sort) {
        return PageResponse.from(audit.search(q, type, pageableFactory.audit(page, size, sort)));
    }
}
