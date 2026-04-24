
git add .

git commit -m "인증기능2차"

git push -u origin main





git init
git add .
git commit -m "first commit"
git remote add origin https://github.com/GarpsuKim/KootPanKingThree.git
git branch -M main
git push -u origin main





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