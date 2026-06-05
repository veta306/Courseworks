package com.example.demo.entity;

import com.example.demo.entity.enums.DisciplineType;
import com.example.demo.entity.enums.MatchLevel;
import com.example.demo.entity.enums.PlagiarismCheckStatus;
import com.example.demo.entity.enums.WorkState;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "works")
public class Work {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String theme;
    private String classroomLink;
    private String fullTextLink;
    private String shortTextLink;
    private String studentGroup;
    private String topicDistributionLink;
    private LocalDateTime turnInDate;

    private String googleSubmissionLink;

    @Enumerated(EnumType.STRING)
    private MatchLevel isCorrectStudent;

    @Enumerated(EnumType.STRING)
    private MatchLevel isCorrectSupervisor;

    @Enumerated(EnumType.STRING)
    private MatchLevel isCorrectTheme;

    @Enumerated(EnumType.STRING)
    private DisciplineType type;

    @Enumerated(EnumType.STRING)
    private WorkState state;

    @Enumerated(EnumType.STRING)
    private PlagiarismCheckStatus plagiarismCheckStatus;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private User student;

    @Column(columnDefinition="TEXT")
    private String rawStudentName;

    @ManyToOne
    @JoinColumn(name = "supervisor_id")
    private User supervisor;

    @Column(columnDefinition="TEXT")
    private String rawSupervisorName;

    @ManyToOne
    @JoinColumn(name = "reviewer_id")
    private User reviewer;
    private String rawReviewerName;

    @OneToOne
    @JoinColumn(name = "plagiarism_report", referencedColumnName = "id")
    private PlagiarismReport plagiarismReport;

    @Column(columnDefinition="TEXT")
    private String themeDifference;

    @Column(columnDefinition="TEXT")
    private String studentDifference;

    @Column(columnDefinition="TEXT")
    private String supervisorDifference;

    @Column(columnDefinition="TEXT")
    private String ministryDifference;

    @Column(columnDefinition="TEXT")
    private String HEIDifference;

    @Column(columnDefinition="TEXT")
    private String departmentDifference;

    @Column(columnDefinition="TEXT")
    private String groupDifference;

    @Column(columnDefinition="TEXT")
    private String cityYearDifference;

    @Column(columnDefinition="TEXT")
    private String externalIdCode;

    @Column(columnDefinition="TEXT")
    private String nameAtTitlePageDifference;

    @Column(columnDefinition="TEXT")
    private String fileNameToCopy;

    @Column(columnDefinition="TEXT")
    private String googleSubmissionId;
}
