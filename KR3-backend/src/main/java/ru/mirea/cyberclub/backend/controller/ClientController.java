package ru.mirea.cyberclub.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientRequest;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientResponse;
import ru.mirea.cyberclub.backend.service.ClientService;

/** REST API клиентов клуба. */
@RestController
@RequestMapping("/api/clients")
@Tag(name = "Клиенты")
public class ClientController {
    private final ClientService clients;

    public ClientController(ClientService clients) {
        this.clients = clients;
    }

    @GetMapping
    @Operation(summary = "Список клиентов; q — поиск по нику, ФИО, телефону, email")
    public List<ClientResponse> list(@RequestParam(required = false) String q) {
        return clients.list(q);
    }

    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable Long id) {
        return clients.get(id);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clients.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить клиента (нужен актуальный version)")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clients.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить клиента без бронирований")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        clients.delete(id);
        return ResponseEntity.noContent().build();
    }
}
