package com.example.demo.dto;

import java.time.LocalDateTime;

import com.example.demo.entity.Work;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkDTO {
  public Work work;
  public LocalDateTime latestActionDate;
}
