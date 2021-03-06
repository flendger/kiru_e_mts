package ru.mtsteta.courses.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import ru.mtsteta.courses.domain.Course;
import ru.mtsteta.courses.dto.LessonDto;
import ru.mtsteta.courses.dto.UserDto;
import ru.mtsteta.courses.exceptions.NotFoundException;
import ru.mtsteta.courses.service.CourseService;
import ru.mtsteta.courses.service.StatisticsCounter;
import ru.mtsteta.courses.service.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/course")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService courseService;
    private final StatisticsCounter statisticsCounter;
    private final UserService userService;

    @GetMapping("/interesting")
    @ResponseBody
    public List<Course> getInterestingCourses() {
        statisticsCounter.countHandlerCall();
        // У нас есть бизнес инсайт, что все интересные курсы написал Вася
        return courseService.coursesByAuthor("Вася");
    }

    @GetMapping
    public String courseTable(Model model, @RequestParam(name = "titlePrefix", required = false) String titlePrefix) {
        model.addAttribute("courses", courseService.findByTitlePrefix((titlePrefix == null ? "" : titlePrefix) + "%")
                .stream()
                .sorted(Comparator.comparing(Course::getId))
                .collect(Collectors.toList()));
        model.addAttribute("activePage", "courses");
        return "course_list";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    @Transactional
    public String editCourse(Model model, @PathVariable("id") Long id, HttpServletRequest request) {
        Course course = courseService.findById(id).orElseThrow(() -> new NotFoundException(String.format("Course [%d] not found", id)));
        model.addAttribute("course", course);
        model.addAttribute("lessons", course.getLessons()
                .stream()
                .map(LessonDto::from)
                .sorted(Comparator.comparing(LessonDto::getId))
                .collect(Collectors.toList()));

        if (request.isUserInRole("ROLE_ADMIN")) {
            model.addAttribute("users", userService.findAllByCoursesNotContains(course));
        } else {
            String username = request.getRemoteUser();
            UserDto user = userService.findUserDtoByUsername(username)
                    .orElseThrow(() -> new NotFoundException(String.format("User [%s] not found", username)));
            model.addAttribute("users", Collections.singletonList(user));
        }
        return "course_form";
    }

    @Secured("ROLE_ADMIN")
    @PostMapping
    public String saveCourse(@Valid Course course, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "course_form";
        }

        courseService.save(course);
        return "redirect:/course";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/assign")
    public String assignUserToCourse(@PathVariable("id") Long courseId, @RequestParam("userId") Long userId, HttpServletRequest request) {
        if (!request.isUserInRole("ROLE_ADMIN")) {
            UserDto user = userService.findById(userId)
                    .orElseThrow(() -> new NotFoundException(String.format("User [%d] not found", userId)));
            if (!user.getUsername().equals(request.getRemoteUser())) {
                return "redirect:/access_denied";
            }
        }
        courseService.assignUserToCourse(courseId, userId);

        return "redirect:/course/" + courseId;
    }

    @Secured("ROLE_ADMIN")
    @DeleteMapping("/{id}/dismiss")
    public String dismissUserFromCourse(@PathVariable("id") Long courseId, @RequestParam("userId") Long userId) {
        courseService.dismissUserFromCourse(courseId, userId);

        return "redirect:/course/" + courseId;
    }

    @GetMapping("/new")
    public String createCourse(Model model) {
        model.addAttribute("course", new Course());
        return "course_form";
    }

    @Secured("ROLE_ADMIN")
    @DeleteMapping("/{id}")
    public String deleteCourse(@PathVariable Long id) {
        courseService.delete(id);
        return "redirect:/course";
    }

    @ExceptionHandler
    public ModelAndView notFoundExceptionHandler(NotFoundException ex, Model model) {
        ModelAndView modelAndView = new ModelAndView("not_found");
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        model.addAttribute("msg", ex.getMessage());
        return modelAndView;
    }
}
