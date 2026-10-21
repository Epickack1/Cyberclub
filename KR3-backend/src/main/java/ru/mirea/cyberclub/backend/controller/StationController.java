package ru.mirea.cyberclub.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import ru.mirea.cyberclub.backend.dto.StationDtos.StationActiveRequest;
import ru.mirea.cyberclub.backend.dto.StationDtos.StationRequest;
import ru.mirea.cyberclub.backend.dto.StationDtos.StationResponse;
import ru.mirea.cyberclub.backend.service.StationService;

/** REST API игровых мест — справочника. Удаления нет: место включается и отключается. */
@RestController
@RequestMapping("/api/stations")
@Tag(name = "Игровые места (справочник)")
public class StationController {
    private final StationService stations;

    public StationController(StationService stations) {
        this.stations = stations;
    }

    @GetMapping
    @Operation(summary = "Список мест; activeOnly=true — только работающие")
    public List<StationResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return stations.list(activeOnly);
    }

    @GetMapping("/{id}")
    public StationResponse get(@PathVariable Long id) {
        return stations.get(id);
    }

    @PostMapping
    public ResponseEntity<StationResponse> create(@Valid @RequestBody StationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stations.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить место (нужен актуальный version)")
    public StationResponse update(@PathVariable Long id, @Valid @RequestBody StationRequest request) {
        return stations.update(id, request);
    }

    @PostMapping("/{id}/active")
    @Operation(summary = "Включить или перевести на обслуживание")
    public StationResponse setActive(@PathVariable Long id, @Valid @RequestBody StationActiveRequest request) {
        return stations.setActive(id, request.active());
    }
}
