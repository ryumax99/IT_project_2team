package webproject_2team.lunch_matching.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import webproject_2team.lunch_matching.domain.Board;
import webproject_2team.lunch_matching.domain.Comment;
import webproject_2team.lunch_matching.dto.PageRequestDTO;
import webproject_2team.lunch_matching.dto.PageResponseDTO;
import webproject_2team.lunch_matching.security.dto.CustomUserDetails;
import webproject_2team.lunch_matching.service.BoardService;
import webproject_2team.lunch_matching.service.CommentService;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Log4j2
public class BoardController {

    private final BoardService boardService;
    private final CommentService commentService;

    @Value("${kakao.javascript.api.key}")
    private String kakaoJavascriptApiKey;

    // =================================================================
    // 1. 페이지 조회 (GET Mappings)
    // =================================================================

    /**
     * 게시글 목록 페이지
     */
    @GetMapping("/board/list")
    public String list(PageRequestDTO pageRequestDTO, Model model) {
        PageResponseDTO<Board> responseDTO = boardService.getBoardList(pageRequestDTO);
        model.addAttribute("responseDTO", responseDTO);
        model.addAttribute("pageRequestDTO", pageRequestDTO);
        return "board_list";
    }

    /**
     * 게시글 상세 조회 페이지
     */
    @GetMapping("/board/read")
    public String read(@RequestParam("id") Long id,
                       PageRequestDTO pageRequestDTO, // page, type, keyword 등을 이 객체로 한번에 받습니다.
                       Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails,
                       RedirectAttributes redirectAttributes) {

        Board board = boardService.read(id);
        if (board == null) {
            redirectAttributes.addFlashAttribute("error_message", "존재하지 않는 게시글입니다.");
            return "redirect:/board/list";
        }

        // --- 접근 권한 검사 시작 ---
        boolean canAccess = false;
        boolean isAdmin = userDetails != null && userDetails.isAdmin(); // 관리자 여부 확인

        // 관리자(ROLE_ADMIN)인 경우 모든 게시글에 접근 가능
        if (isAdmin) {
            canAccess = true;
        }

        // 1. 본인 글인지 확인
        if (userDetails != null && userDetails.getEmail().equals(board.getWriterEmail())) {
            canAccess = true;
        }

        // 2. 성별 제한이 없는 글인지 확인
        if (!canAccess && "성별상관무".equals(board.getGenderLimit())) {
            canAccess = true;
        }

        // 3. 성별 제한이 있는 글일 경우 (수정된 로직)
        if (!canAccess && userDetails != null) {
            String userGender = userDetails.getGender(); // 예: "female"
            String boardLimit = board.getGenderLimit();  // 예: "여"

            // <<--- 값 변환 로직 추가 ---
            if ("female".equalsIgnoreCase(userGender)) {
                userGender = "여";
            } else if ("male".equalsIgnoreCase(userGender)) {
                userGender = "남";
            }
            // --- 값 변환 로직 끝 --- >>

            // 변환된 값으로 비교
            if (userGender != null && userGender.equals(boardLimit)) {
                canAccess = true;
            }
        }

// 4. 최종 접근 거부 처리
        if (!canAccess) {
            redirectAttributes.addFlashAttribute("error_message", "이 게시글에 접근할 권한이 없습니다.");
            return "redirect:/board/list" + pageRequestDTO.getLink();
        }
// --- 접근 권한 검사 끝 ---

        // --- 모델에 데이터 추가 ---
        model.addAttribute("board", board);
        model.addAttribute("comments", commentService.getCommentsByBoardId(id));
        model.addAttribute("kakaoJsApiKey", kakaoJavascriptApiKey);
        // DTO 객체 전체를 모델에 추가합니다.
        model.addAttribute("pageRequestDTO", pageRequestDTO);

        if (userDetails != null) {
            model.addAttribute("loggedInNickname", userDetails.getNickname());
            model.addAttribute("loggedInUserEmail", userDetails.getEmail());
            model.addAttribute("isAdmin", isAdmin); // 관리자 여부를 템플릿으로 전달 (추가)
        }
        return "read";
    }

    /**
     * 게시글 작성 페이지
     */
    @GetMapping("/board/register")
    public String registerForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) { // <-- 1. 로그인 정보 받기
        model.addAttribute("kakaoJsApiKey", kakaoJavascriptApiKey);
        model.addAttribute("board", new Board());

        // ===== 로그인 사용자 정보 처리 시작 =====
        // 2. 로그인한 사용자의 닉네임을 모델에 담아 HTML로 전달
        //    (작성자 입력란에 자동으로 채워넣기 위함)
        if (userDetails != null) {
            model.addAttribute("loggedInNickname", userDetails.getNickname());
        }
        // ===== 로그인 사용자 정보 처리 끝 =====
        return "board_register";
    }

    /**
     * 게시글 수정 페이지
     */
    @GetMapping("/board/modify/{id}")
    public String modifyForm(@PathVariable("id") Long id, Model model,
                             @AuthenticationPrincipal CustomUserDetails userDetails, // userDetails 추가
                             RedirectAttributes redirectAttributes
                            ) {
        // 수정 페이지는 기존 데이터를 불러오므로, 현재 로그인 정보는 필요하지 않습니다.
        Board board = boardService.read(id);
// 추가!
        if (board == null) {
            redirectAttributes.addFlashAttribute("error_message", "존재하지 않는 게시글입니다.");
            return "redirect:/board/list";
        }


        // **수정 권한 확인 로직 추가**
        boolean isAdmin = userDetails != null && userDetails.isAdmin();
        if (userDetails == null || !(isAdmin || board.getWriterEmail().equals(userDetails.getEmail()))) {
            redirectAttributes.addFlashAttribute("error_message", "게시글을 수정할 권한이 없습니다.");
            return "redirect:/board/read?id=" + id; // 권한 없으면 상세 페이지로 리다이렉트

        }

        model.addAttribute("board", board);
        model.addAttribute("kakaoJsApiKey", kakaoJavascriptApiKey);
        model.addAttribute("loggedInNickname", userDetails.getNickname());
        return "board_modify";
    }

    // =================================================================
    // 2. 폼 제출 처리 (POST Mappings)
    // =================================================================

    /**
     * 게시글 등록 처리
     */
    @PostMapping("/board/register")
    public String register(@ModelAttribute Board board,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes)
                            throws IOException {
        // ===== 로그인 사용자 처리 시작 =====
        if (userDetails == null) {
            return "redirect:/login"; // 1. 비로그인 시 로그인 페이지로
        }
        // 2. Board 객체에 작성자 닉네임과 이메일 설정
        board.setWriter(userDetails.getNickname());
        board.setWriterEmail(userDetails.getEmail());
        // ===== 로그인 사용자 처리 끝 =====

        board.setCreatedAt(LocalDateTime.now());
        boardService.save(board);
        redirectAttributes.addFlashAttribute("success_message", "게시글이 성공적으로 등록되었습니다.");
        return "redirect:/board/list";
    }

    /**
     * 게시글 수정 처리
     */
    @PostMapping("/board/modify")
    public String modify(@ModelAttribute Board board,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes,
                         Model model) throws IOException {
        if (userDetails == null) {
            return "redirect:/login";
        }
        try {
            // ... 기존 유효성 검사 ...
            // 서비스 호출 시, 권한 확인을 위해 현재 로그인한 사용자의 이메일과 isAdmin 여부 전달
            boardService.modify(board, userDetails.getEmail(), userDetails.isAdmin());
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error_message", "게시글 수정 권한이 없습니다.");
            // 서비스에서 권한 없음 예외 발생 시 에러 페이지로 이동
            return "redirect:/error/access-denied"; // (에러 페이지는 별도 구현 필요)
        }
        redirectAttributes.addFlashAttribute("success_message", "게시글이 성공적으로 수정되었습니다.");
        return "redirect:/board/read?id=" + board.getId();
    }

    /**
     * 게시글 삭제 처리
     */
    @PostMapping("/board/delete/{id}")
    public String delete(@PathVariable("id") Long id,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        try {
            // 서비스 호출 시, 권한 확인을 위해 현재 로그인한 사용자의 이메일과 isAdmin 여부 전달
            boardService.delete(id, userDetails.getEmail(), userDetails.isAdmin());
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error_message", "게시글 삭제 권한이 없습니다.");
            return "redirect:/error/access-denied";
        }
        redirectAttributes.addFlashAttribute("success_message", "게시글이 성공적으로 삭제되었습니다.");
        return "redirect:/board/list";
    }


}
