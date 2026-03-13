package com.sscm.student.service;

import com.sscm.auth.entity.Student;
import com.sscm.auth.repository.StudentRepository;
import com.sscm.common.exception.BusinessException;
import com.sscm.common.exception.ErrorCode;
import com.sscm.student.dto.*;
import com.sscm.student.entity.RecordCategory;
import com.sscm.student.entity.StudentRecord;
import com.sscm.student.repository.StudentRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentRecordService {

    private final StudentRecordRepository studentRecordRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public StudentRecordResponse createRecord(StudentRecordRequest request, Long currentUserId) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        StudentRecord record = StudentRecord.builder()
                .student(student)
                .year(request.getYear())
                .semester(request.getSemester())
                .category(request.getCategory())
                .content(request.getContent())
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        return StudentRecordResponse.from(studentRecordRepository.save(record));
    }

    @Transactional
    public StudentRecordResponse updateRecord(Long recordId, StudentRecordUpdateRequest request, Long currentUserId) {
        StudentRecord record = studentRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        record.updateContent(request.getContent(), currentUserId);
        return StudentRecordResponse.from(record);
    }

    @Transactional
    public void deleteRecord(Long recordId) {
        StudentRecord record = studentRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        studentRecordRepository.delete(record);
    }

    public StudentRecordResponse getRecord(Long recordId) {
        StudentRecord record = studentRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return StudentRecordResponse.from(record);
    }

    public List<StudentRecordResponse> getStudentRecords(Long studentId, Integer year, Integer semester,
                                                          RecordCategory category) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        List<StudentRecord> records;
        if (category != null) {
            records = studentRecordRepository.findByStudentIdAndYearAndSemesterAndCategory(
                    studentId, year, semester, category);
        } else {
            records = studentRecordRepository.findByStudentIdAndYearAndSemester(studentId, year, semester);
        }

        return records.stream().map(StudentRecordResponse::from).toList();
    }

    public StudentInfoResponse getStudentInfo(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
        return StudentInfoResponse.from(student);
    }

    public List<StudentInfoResponse> getAllStudents() {
        return studentRepository.findAll().stream()
                .map(StudentInfoResponse::from)
                .toList();
    }
}
