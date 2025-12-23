package com.example.applicationservice.util;

import com.example.applicationservice.dto.ApplicationDto;

import java.util.List;

public record ApplicationPage(List<ApplicationDto> items, String nextCursor) { }
