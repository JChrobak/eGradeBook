package pl.polsl.egradebook.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.polsl.egradebook.model.entities.Case;
import pl.polsl.egradebook.model.entities.Message;
import pl.polsl.egradebook.model.entities.Presence;
import pl.polsl.egradebook.model.entities.Student;
import pl.polsl.egradebook.model.entities.StudentsClass;
import pl.polsl.egradebook.model.entities.Teacher;
import pl.polsl.egradebook.model.entities.User;
import pl.polsl.egradebook.model.repositories.CaseRepository;
import pl.polsl.egradebook.model.repositories.GradeRepository;
import pl.polsl.egradebook.model.repositories.LessonRepository;
import pl.polsl.egradebook.model.repositories.MessageRepository;
import pl.polsl.egradebook.model.repositories.PresenceRepository;
import pl.polsl.egradebook.model.repositories.StudentRepository;
import pl.polsl.egradebook.model.repositories.StudentsClassRepository;
import pl.polsl.egradebook.model.repositories.TeacherRepository;
import pl.polsl.egradebook.model.repositories.UserRepository;
import pl.polsl.egradebook.model.util.StringValidator;
import pl.polsl.egradebook.model.util.UrlValidator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Controller
@RequestMapping("/teacher")
public class TeacherController {


    private final TeacherRepository teacherRepository;

    private final StudentRepository studentRepository;

    private final LessonRepository lessonRepository;

    private final UserRepository userRepository;

    private final GradeRepository gradeRepository;

    private final StudentsClassRepository studentsClassRepository;

    private final PresenceRepository presenceRepository;

    private final CaseRepository caseRepository;

    private final MessageRepository messageRepository;

    public TeacherController(TeacherRepository teacherRepository, StudentRepository studentRepository, LessonRepository lessonRepository, UserRepository userRepository, GradeRepository gradeRepository, StudentsClassRepository studentsClassRepository, PresenceRepository presenceRepository, CaseRepository caseRepository, MessageRepository messageRepository) {
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
        this.gradeRepository = gradeRepository;
        this.studentsClassRepository = studentsClassRepository;
        this.presenceRepository = presenceRepository;
        this.caseRepository = caseRepository;
        this.messageRepository = messageRepository;
    }

    @GetMapping()
    @PreAuthorize("hasAuthority('/teacher')")
    public String viewGradesAndAttendance(Authentication authentication, Model model) {
        User loggedTeacher = this.getTeacherByUserName(authentication.getName()).getUser();
        Iterable<Student> classStudents = studentRepository.findAll();
        model.addAttribute("teacher", loggedTeacher);
        model.addAttribute("grades", gradeRepository.findAllByOrderByStudent_User_SurnameAsc());
        model.addAttribute("attendance", presenceRepository.findAllByLesson_Teacher_User_UserID_OrderByDateDesc(loggedTeacher.getUserID()));
        model.addAttribute("students", studentRepository.findAll());
        return "teacher-view";
    }

    //submits changed attendances for selected class
    //returns teacher-attendance-management.html with model attribute submitSuccessful set to true to show the message on page
    @PostMapping("/attendance/submit")
    @PreAuthorize("hasAuthority('/teacher/attendance/submit')")
    public String submitAttendance(ListWrapper listWrapper, Model model, Authentication authentication) {
        List<Presence> studentAttendance = listWrapper.getList();
        for (Presence p : studentAttendance) {
            presenceRepository.save(p);
        }
        Teacher loggedTeacher = this.getTeacherByUserName(authentication.getName());
        int teacherID = loggedTeacher.getTeacherID();
        model.addAttribute("submitSuccessful", true);
        model.addAttribute("todayDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        model.addAttribute("lessons", lessonRepository.findAllByTeacher_TeacherID(teacherID));
        return "teacher-attendance-management";
    }

    //shows dropdown box with classes and enables class selection for changing attendances
    //sends selected lesson with post to the same page, along with the selected date from date picker
    //returns teacher-attendance-management.html with model attribute newInstance set to true to suppress all messages about empty date and list of students
    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('/teacher/attendance')")
    public String checkAttendance(Authentication authentication, Model model) {
        Teacher loggedTeacher = this.getTeacherByUserName(authentication.getName());
        int teacherID = loggedTeacher.getTeacherID();
        model.addAttribute("newInstance", true);
        model.addAttribute("todayDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        model.addAttribute("lessons", lessonRepository.findAllByTeacher_TeacherID(teacherID));
        return "teacher-attendance-management";
    }

    //shows all students in class for easy setting of attendance in selected lesson
    //gets selected lesson with post from the dropdown list along with selected date
    //sets model attributes selectedDate and studentsFound to false to warn about incorrect or nonsensical input
    //submits the data if correct using the second form in teacher-attendance-management.html to /teacher/attendance/submit
    @PostMapping("/attendance")
    @PreAuthorize("hasAuthority('/teacher/attendance')")
    public String checkAttendancePost(Authentication authentication, Model model, @RequestParam("selectedLesson") int selectedLesson, @RequestParam("selectedDate") String selectedDate) {
        Teacher loggedTeacher = this.getTeacherByUserName(authentication.getName());
        int teacherID = loggedTeacher.getTeacherID();
        model.addAttribute("todayDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        model.addAttribute("lessons", lessonRepository.findAllByTeacher_TeacherID(teacherID));


        if (!StringValidator.validateDate(selectedDate))
            model.addAttribute("selectedDate", false);
        else {
            model.addAttribute("selectedDate", selectedDate);
            StudentsClass selectedClass = lessonRepository.findByLessonID(selectedLesson).getStudentsClass();
            List<Student> students = studentRepository.findAllByStudentsClass_ClassID(selectedClass.getClassID());
            if (students == null || students.size() == 0)
                model.addAttribute("studentsFound", false);
            else {
                List<Presence> newPresences = presenceRepository.findAllByDateAndLesson_LessonID(selectedDate, selectedLesson);
                if (newPresences.size() == 0) {
                    newPresences = new ArrayList<>();
                    for (Student s : students) {
                        Presence p = new Presence();
                        p.setDate(selectedDate);
                        p.setLesson(lessonRepository.findByLessonID(selectedLesson));
                        p.setStudent(s);
                        p.setPresent(false);
                        presenceRepository.save(p);
                        newPresences.add(p);
                    }
                }
                ListWrapper listWrapper = new ListWrapper();
                listWrapper.setList(newPresences);
                model.addAttribute("studentsAttendance", listWrapper);
            }
        }
        return "teacher-attendance-management";
    }

    //case management
    @GetMapping("/cases")
    @PreAuthorize("hasAuthority('/teacher/cases')")
    public String getCaseManagementSite(Authentication authentication, Model model) {
        User loggedTeacher = this.getTeacherByUserName(authentication.getName()).getUser();
        model.addAttribute("cases", caseRepository.findByReceiver_UserIDOrSender_UserID(loggedTeacher.getUserID(), loggedTeacher.getUserID()));
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("newCase", new Case());
        this.addHomeUrl(model);
        return "case-management";
    }

    // add case
    @PostMapping("/cases/add")
    @PreAuthorize("hasAuthority('/teacher/cases/add')")
    public String addCase(@ModelAttribute("newCase") Case newCase, @RequestParam("content") String msgContent, Model model, Authentication authentication) {

        User loggedTeacher = this.getTeacherByUserName(authentication.getName()).getUser();

        newCase.setSender(loggedTeacher);
        caseRepository.save(newCase);
        model.addAttribute("cases", caseRepository.findByReceiver_UserID(loggedTeacher.getUserID()));
        Message newMessage = new Message();
        newMessage.setContent(msgContent);
        newMessage.setReferencedCase(newCase);
        newMessage.setSender(userRepository.findUserByUserName(authentication.getName()));
        messageRepository.save(newMessage);

        this.addHomeUrl(model);
        return "redirect:/teacher/cases/" + newCase.getCaseID() + "/";
    }

    //cases view for the teacher
    @GetMapping(path = "/cases/{caseID}")
    @PreAuthorize("hasAuthority('/teacher/cases/{caseID}')")
    public String selectedCase(@PathVariable("caseID") int caseID, Model model, Authentication authentication) {

        Teacher loggedTeacher = this.getTeacherByUserName(authentication.getName());
        if (!UrlValidator.canAccessCase(caseID, loggedTeacher.getUser().getUserID(), caseRepository))
            return "access-denied";
        Case foundCase = caseRepository.findByCaseID(caseID);
        model.addAttribute("case", foundCase);
        List<Message> messages = messageRepository.findAllByReferencedCase_CaseID(caseID);
        model.addAttribute("messages", messages);
        this.addHomeUrl(model);
        return "case-content-view";
    }

    //reply to case
    @PostMapping(path = "/cases/reply")
    @PreAuthorize("hasAuthority('/teacher/cases/reply')")
    public String replyToCase(@RequestParam("caseID") int caseID, @RequestParam("content") String content, Authentication authentication) {
        Message newMessage = new Message();
        newMessage.setReferencedCase(caseRepository.findByCaseID(caseID));
        newMessage.setContent(content);
        newMessage.setSender(userRepository.findUserByUserName(authentication.getName()));
        messageRepository.save(newMessage);
        return "redirect:/teacher/cases/" + caseID + "/";
    }

    private Teacher getTeacherByUserName(String userName) {
        return teacherRepository.findByUser_UserName(userName);
    }

    private void addHomeUrl(Model model) {
        model.addAttribute("homeUrl", "/teacher/");
    }

    //list wrapper used to wrap the presence list and send it via post
    class ListWrapper {
        private List<Presence> list;

        public ListWrapper() {
            this.list = new ArrayList<>();
        }

        public void setList(List<Presence> list) {
            this.list = list;
        }

        public void addPresence(Presence presence) {
            this.list.add(presence);
        }

        public List<Presence> getList() {
            return list;
        }
    }

}