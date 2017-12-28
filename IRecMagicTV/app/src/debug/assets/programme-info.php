
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<meta name="viewport" content="width=320">
<title>Magic TV 遙距錄影</title>
<style type="text/css" media="screen">
@import "main.css";
</style>
</head>
<body>
<script type="text/javascript">
function search_focus(el)
{
if(el.V) {
if (el.value == el.V) {
el.value = '';
}
} else {
el.V = el.value;
el.value = '';
}
}
function setbg(color)
{
document.bgColor=color;
document.body.style.backgroundColor=color;
}
function jump(channelID)
{
var channel = channelID
window.location="programme-list.php?channelsel="+channel;
}
</script>
<div id="container">
<div id="toppanel"><img src="images/mtvlogo.png" alt="Magic TV" width="55" height="76">
<div id="toplinks">
<ul>
<li><a href=unit-list.php>~MTV7000D</a></li>
<li><a href=setlangENG.php >English</a></li>
<li><a href=logout.php>登出</a></li>
</ul>
</div>
</div>
<div id="headline">
<div class="formbutton"><a class="buttonsmall" href="javascript:jump(11);"><span>返回</span></a>
</div>
</div>
<div class="clear"></div>
<div id="proghead">
<h3 class="black">01:35 - 02:00</h3>
<h3>01-01-2015</h3>
<h2>恩雨之聲 (粵/國)(S)</h2>
</div>
<div id="recordoptions">
<ul>
<li><a href=presingleRec.php class="singlerec">單一錄影</a></li>
<li><a href=repeat-record.php class="repeatrec">重複錄影</a></li>
</ul>
</div>
<div id="proginfo">
<p>  </p>
</div>
<div class="clear"></div>
<div id="footer"><a href=http://www.magictv.com/hk/zh-b5/index.html>&copy; 視科系統有限公司</a></div>
</div>
</body>
</html>
