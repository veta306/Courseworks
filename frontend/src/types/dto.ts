export interface Principal {
  attributes: {
    email: number;
    name: string;
    picture: string;
  };
  authorities: { authority: string }[];
}

export interface User {
  id: number;
  name: string;
  email: string;
  roles: Role[];
}

export interface Role {
  id: number;
  name: string;
}

export interface Department {
  id: number;
  name: string;
  responsibleUser: User;
  headUsers: User[];
  disciplines: Discipline[];
}

export type NameFormat =
  | "ALL"
  | "SURNAME_NAME"
  | "SURNAME_I"
  | "SURNAME_IB"
  | "SURNAME_NAME_PATRONYMIC";

export type PageNumberLocation = "TOP" | "BOTTOM" | "ANY";
export type Visibility = "PUBLIC" | "PRIVATE";
export type Template = "DISCIPLINE" | "GROUP" | "STUDENT" | "TYPE";

export type DisciplineType = "COURSEWORK" | "QUALIFICATION_WORK";

export interface Discipline {
  id: number;
  name: string;
  year: number;
  topicDistributionLink: string;
  googleClassId: string;
  googleClassLink: string;
  googleAssignmentId: string;
  googleAssignmentLink: string;
  googleDriveFolderLink: string;
  updateDate: string;
  updating: boolean;
  type: DisciplineType;
  students: User[];
  supervisors: User[];
  works: Work[];
  nameFormat: NameFormat;

  pageNumberLocation: PageNumberLocation;
  visibility: Visibility;
}

export interface DisciplineDTO {
  name: string;
  year: number;
  works: Work[];
}

type MatchLevel = "NOT_MATCHED" | "LOW" | "MEDIUM" | "HIGH";
type PlagiarismCheckStatus = "NOT_CHECKED" | "CHECKED" | "IN_PROGRESS";

export interface PlagiarismReport {
  id: number;
  fullReportLink: string;
  shortReportLink: string;
}

export interface Work {
  id: number;
  theme: string | null;
  classroomLink: string;
  fullTextLink: string | null;
  shortTextLink: string | null;
  studentGroup: string | null;
  googleSubmissionLink: string;
  isCorrectStudent: MatchLevel;
  isCorrectSupervisor: MatchLevel;
  isCorrectTheme: MatchLevel;
  type: DisciplineType;
  student: User;
  rawStudentName: string;
  supervisor: User | null;
  rawSupervisorName: string;
  reviewer: User | null;
  plagiarismReport: PlagiarismReport | null;
  plagiarismCheckStatus: PlagiarismCheckStatus;

  themeDifference: string;

  studentDifference: string;
  supervisorDifference: string;
  ministryDifference: string;
  heidifference: string;
  departmentDifference: string;
  groupDifference: string;
  cityYearDifference: string;
  // new field (needed?)
  topicDistributionLink: string;
  externalIdCode: string;
  nameAtTitlePageDifference: string;
  fileNameToCopy: string;
  turnInDate: string;
  googleSubmissionId: string;
}

export type WorkDTO = {
  work: Work;
  latestActionDate: string | null;
}

export type Notification = {
  id: number;
  message: string;
  targetUrl: string;
  createdAt: string;
  type: string;
  read: boolean;
};
