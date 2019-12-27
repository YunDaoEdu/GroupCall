(function ($) {
    "use strict";

    let uName = $("#u_name"),
        uRoom = $("#room_id"),
        subBtn = $("#sub_btn"),
        errBox = $("#err_box"),
        errContent = $("#err_con"),
        errClose = $("#close_err");

    /**
     * 点击了提交
     */
    subBtn.on("click", function () {
        $(this).attr("disabled", true);

        let userName = uName.val().trim(),
            userRoom = uRoom.val().trim();

        let reg = new RegExp("^[a-zA-Z0-9]{1,7}$");

        if (!reg.test(userName)) {
            openError("用户名必须是1～7个字母或数字");
            return;
        }

        if (!reg.test(userRoom)) {
            openError("房间号必须是1～7个字母或数字");
            return;
        }

        $.ajax({
            url: "/register",
            type: "post",
            dataType: "json",
            data: {
                uName: userName,
                uRoom: userRoom
            },
            success: function (result) {
                let status = result.status;

                if (status === 200) {
                    window.location.href = "/chat";
                    return;
                }

                openError(result.info);
            },
            error: function (err) {
                openError(err);
            }
        });
    });


    /**
     * 错误提示
     */
    function openError(msg) {
        errBox.css("display", "block");
        errContent.text(msg);

        subBtn.attr("disabled", false);
    }


    /**
     * 关闭错误
     */
    errClose.on("click", function () {
        errContent.text("");
        errBox.css("display", "none");
    });
})(window.jQuery);