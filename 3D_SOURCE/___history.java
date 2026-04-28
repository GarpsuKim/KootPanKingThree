
git add .

git commit -m "위치추적3"

git push -u origin main





git init
git add .
git commit -m "first commit"
git remote add origin https://github.com/GarpsuKim/KootPanKingThree.git
git branch -M main
git push -u origin main








1. [mainMenu / Help / Auto-Start on Boot] : 등록되어 있는 상태에서 해제하려면, [Admin Reg(관리자 인증)]을 통과해야만 하도록 수정

2. [mainMenu / Image&Camera]  : 서브메뉴 추가 '녹화된 mp4 파일들...' --> fileChooser호출  --> 시스템 기본 플레이어 호출

3. [Admin Reg] 네이버 등록 기능 추가
--> 텍스트 2개 : 네이버 id , 네이버 비밀번호
--> 적당한 위치에 '(네이버 칼렌다 일정관리) 기능을 이용하는데 필요합니다'라고 표시
--> 버튼 1개 : '네이버 메일 수신 확인'표시
--> 버튼 누르면, 4자리 난수 숫자를, 이미 먼저 등록되어 있는 gmail에서  수신자 xxxxx@naver.com 으로 발송
--> 텍스트 1개 : 네이버 수신 난수 숫자 입력 --> 값 확인 ---> ini 등록 
naver.caldav.id , naver.caldav.password , naver.certified.yymmddhhmmss(신규항목)
"xxxxx@naver.com"  -->  gmail.lastTo  

4. [Admin Reg] 다이알로그에 스타일 적용, 프로페셔날 느낌이 나도록
ㅡ Naver CalDAV Settings 다이알로그 참고하여 이와 유사하게
ㅡ 전체적인 좌우상하 싸이즈를 현재보다 대폭 크게 확장

5. [ MainMenu / Tools / GmailCalendar / Naver Setting ] : 메뉴 삭제 --> [Admin Reg]에 통합

6. [ MainMenu / Tools / GmailCalendar / Gmail Setup ] : 메뉴 삭제 --> [Admin Reg]에 이미 통합

7. [ MainMenu / Tools / TeleGram ] : 메뉴 삭제 --> [Admin Reg]에 이미 통합

8. [ MainMenu / Tools ] 서브 메뉴 개편 : 일정 조회 서브 메뉴들을 전부 레벨업해서 다음 구조로
ㅡ Chime Setting
ㅡ (구분선)
ㅡ 구글 칼렌다 조회 (3일)
ㅡ 구글 칼렌다 조회 (다음 7일)
ㅡ 구글 칼렌다 조회 (지난 7일)
ㅡ 구글 칼렌다 조회 (이번달)
ㅡ 구글 칼렌다 조회 (다음달)
ㅡ (구분선)
ㅡ 네이버 칼렌다 조회 (3일)
ㅡ 네이버 칼렌다 조회 (다음 7일)
ㅡ 네이버 칼렌다 조회 (지난 7일)
ㅡ 네이버 칼렌다 조회 (이번달)
ㅡ 네이버 칼렌다 조회 (다음달)



/*

■  노트북 위치 추적 기능

끝판왕 프로그램에 노트북 위치 추적 기능을 구현했다. 원리는,
1) 노트북에는 GPS장치가 없으므로 스스로는 위도 경도를 알 수 없으나,
2) 노트북 주변의 공유기들을 탐색해서
3) 공유기 고유 식별기호를 Google 데이타베이스에 문의하면
4) Google 데이타베이스가 공유기들의 위치등록 정보(위도 경도값)을 회신해 주고
5) 그 위도 경도 값을 지도에 표시해 주는 방식이다.
최소 5개의 공유기를 검색하고, Google 데이타베이스에 1회 문의 할 때마다, 7원(0.5$) 정도 비용을 신용카드로 지불한다
Google社 데이타베이스에는, 전세계 모든 공유기들의 식별기호와 현재 설치위치 정보가 수록되어 있다. 
정말 무시무시한 DB이다.  軍이 스마트폰을 사용한다는 것은, 자기 현재 위치를 미국(Google)에게 고스란히 노출시키는 행위.......

*/



/*

1. [관리자 등록] 다이얼로그에 버튼 추가 : '현재 위치 활성화',화면 하단 오른쪽
3. [MainMenu / utilities] 에 서브메뉴 추가 : 현재 위치 확인
--> 시스템 기본 브라우저로 현재 위치 호출
4. 텔레그램 명령어 추가 : /where_is_my_laptop , /where
--> 시스템 기본 브라우저로 현재 위치 호출


1. 텔레그램  /secureOff는  관리자 인증 하지 않음
2. 텔레그램 pin message에  cam on / cam off / secure on / secure off 를 항상 표시
3. 동영상 caption의 font 크기를 현재 대비 5배 이상 확대 ( 현재 너무 작아서 안보임 )

*/



/*

"인증기능3차" [적용완료] 2026.4.24.Fri 10:30
"인증기능3-2차" [적용완료] 2026.4.24.Fri 10:30


1. 텔레그램 /cam 명령어 오류 수정
ㅡ 기존 메세지를 지우지 않아야 하는데 지우고 있음, 지우지 말 것. 스크롤 처리 ( 기존 영상이 증거로 사용 될 수 있도록 )
ㅡ 해상도를 좀 낮추어서 전송 속도가 빠르게
ㅡ 'connect'가 되어 있지 않으면, 자동으로 'connect'하여야 함
ㅡ 이미지 하단 중앙에  yyyy/MM/dd hh:mm:ss caption 도입


2. web camera 영상 표시 tab
ㅡ tab 싸이즈가 변하면, 해상도는 변경하지 말고, 언제나 영상 싸이즈를 tab 싸이즈에 fit
ㅡ 이미지 하단 중앙에  yyyy/MM/dd hh:mm:ss caption 도입
ㅡ save 파일에 caption 필수


3. MainMenu / File / '관리자 등록'
ㅡ AppContext.NationCode != "KR" : 영어로 번역 


4. 텔레그램 명령어 추가
-  /SecureOn : web cam 감시 카메라 자동 작동 개시 , 작동 시작하면 자동으로 10초마다 동영상 송신
-  /SecureOff : web cam 감시 카메라 작동 종료

*/


/*

"인증기능2-1차" [적용완료] 2026.4.24.Fri 21:30

1. 구글과 네이버 칼렌다 조회 결과 안내 다이알로그 초기 위치 변경
ㅡ 모니터 갯수가 1개이면 현재 모니터의 좌상단에 맞춤
ㅡ 모니터 갯수가 1개이상이면 제2모니터의 좌상단에 맞춤
ㅡ 위치만 변경, 나머지 기능들은 손대지 말 것

2. KootKangKingLauncher
ㅡ main(arg[0]) 값이 '1' 이면,  AppContext.NationCode = "KR"

3. ini LOAD
if (AppContext.NationCode ==  empty() ),
ㅡ ini에서  App.NationCode 이 존재하면 --> AppContext.NationCode 
ㅡ ini에서  App.NationCode 이 존재하지 않으면, "EN"  --> AppContext.NationCode

4. [MainMenu/File/관리자 등록] 다이알로그
ㅡ 600초 타이머 적용
ㅡ AppContext.NationCode == "KR" : 한국말 메뉴
ㅡ AppContext.NationCode != "KR" : 영어 메뉴

ㅡ 한국이면
ㅡ 버튼 명칭 변경 : 'Gmail 검증'  -->  'Gmail 검증번호 발송'
ㅡ 버튼 명칭 변경 : '텔레그램 검증'  -->  '텔레그램 검증번호 발송'

ㅡ 한국이 아니면
ㅡ 버튼 명칭 변경 : 'Gmail 검증'  -->  'Gmail 검증번호 발송'(영어로)
ㅡ 버튼 명칭 변경 : '텔레그램 검증'  -->  '텔레그램 검증번호 발송'(영어로)

5. [관리자 인증] 다이알로그
ㅡ 300초 타이머 적용
ㅡ AppContext.NationCode == "KR" : 한국말 메뉴
ㅡ AppContext.NationCode != "KR" : 영어 메뉴

ㅡ '인증번호 발송' 버튼 위치 변경 : 인증번호 텍스트의 바로 위, 좌우 폭이 서로 동일하게
ㅡ 안내문 변경 : 관리자 인증이 필요합니다. [인증번호 발송] 버튼을 누르면, 귀하가 미리 등록하신 Gmail 또는 텔레그램으로 4자리 숫자가 발송됩니다. 그 4자리 숫자를 아래의 빈 칸에 정확히 입력해야만 인증 할 수 있습니다.

*/