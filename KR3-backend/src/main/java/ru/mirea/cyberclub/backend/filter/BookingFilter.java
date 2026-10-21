package ru.mirea.cyberclub.backend.filter;

import java.math.BigDecimal;
import java.time.LocalDate;

import ru.mirea.cyberclub.backend.domain.BookingStatus;
import ru.mirea.cyberclub.backend.domain.StationType;

/**
 * Параметры поиска бронирований. Любое поле может быть null — тогда условие не применяется.
 *
 * @param q          свободный поиск: номер брони, ник, ФИО, место, комментарий
 * @param stationId  фильтр по справочнику (игровому месту)
 * @param stationType фильтр по зоне
 * @param clientId   фильтр по клиенту
 * @param status     фильтр по статусу
 * @param paid       фильтр по логическому признаку "оплачено"
 * @param archive    активные, архивные или все
 * @param from       начало периода (дата начала брони, включительно)
 * @param to         конец периода (включительно)
 * @param priceOp    операция сравнения стоимости
 * @param price      значение для сравнения стоимости
 */
public record BookingFilter(
        String q,
        Long stationId,
        StationType stationType,
        Long clientId,
        BookingStatus status,
        Boolean paid,
        ArchiveScope archive,
        LocalDate from,
        LocalDate to,
        NumberOp priceOp,
        BigDecimal price) {

    /** Какие записи показывать с точки зрения архива. */
    public enum ArchiveScope { ACTIVE, ARCHIVED, ALL }

    /** Операции сравнения числового показателя. */
    public enum NumberOp { EQ, GT, GTE, LT, LTE }
}
