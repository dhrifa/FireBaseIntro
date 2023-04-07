package com.example.firebaseintro.ui.login

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.firebaseintro.databinding.ActivityLoginBinding

//import com.firebase.ui.auth.AuthUI
import com.example.firebaseintro.R
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

private const val TAG = "LoginActivity"
const val REQUEST_CODE_SIGNIN = 1

class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding

    //email/pwd sign in
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    //Gmail sign in
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private val REQ_ONE_TAP = 2  // Can be any integer unique to the Activity
    private var showOneTapUI = true
    // ActivityResultLauncher firebase manage all
//    private val signIn: ActivityResultLauncher<Intent> =
//        registerForActivityResult(FirebaseAuthUIActivityResultContract(), this::onSignInResult)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loginEmailPwd = binding.loginEmail
        val loginGmail1 = binding.loginGmail1
        val loginGmail2 = binding.loginGmail2
        val loginGmail3 = binding.loginGmail3
        val loading = binding.loading

        loginViewModel =
            ViewModelProvider(this, LoginViewModelFactory())[LoginViewModel::class.java]
        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
            login.isEnabled = loginState.isDataValid
            loginEmailPwd?.isEnabled = loginState.isDataValid

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        Firebase.initialize(this)
        // Initialize Firebase Auth
//        auth = Firebase.auth
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
//            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        login.setOnClickListener {
            signInWithEmailAndPassword(
                username.text.toString(),
                password.text.toString()
            )
        }
        loginEmailPwd?.setOnClickListener {
            signInWithEmailAndPassword(
                username.text.toString(),
                password.text.toString()
            )
        }
        loginGmail1?.setOnClickListener {
            googleSignInClient.signInIntent.also {
                startActivityForResult(it, REQUEST_CODE_SIGNIN)
            }
        }
        loginGmail2?.setOnClickListener { signInWithGmail() }
        loginGmail3?.setOnClickListener { signInWithGmailSSO() }


        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }
        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                loading.visibility = View.VISIBLE
                loginViewModel.login(username.text.toString(), password.text.toString())
            }
        }
    }

    private fun signInWithGmailSSO() {
        //Gmail sign in
        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setPasswordRequestOptions(
                BeginSignInRequest.PasswordRequestOptions.builder()
                    .setSupported(true)
                    .build()
            )
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    // Your server's client ID, not your Android client ID.
                    .setServerClientId(getString(R.string.default_web_client_id))
                    // Only show accounts previously used to sign in.
//                    .setFilterByAuthorizedAccounts(true)
                    .build()
            )
            .build()
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                val intent = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                signInSSOGoogleLauncher.launch(intent)
            }
            .addOnFailureListener(this) { e ->
                Log.d(TAG, e.localizedMessage)
            }
    }

    private val signInSSOGoogleLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                try {
                    val account = oneTapClient.getSignInCredentialFromIntent(result.data)
                    val credential = GoogleAuthProvider.getCredential(account.googleIdToken, null)
                    auth.signInWithCredential(credential).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(
                                this,
                                "google auth success, 3nd method",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.d("Main Activity TAG", "firebaseAuthWithGoogle: ${account.id}")
                            auth.signOut()
                        }
                    }
                } catch (e: ApiException) {
                    Log.d("Main Activity TAG", "SSOGoogle sign in failed", e)
                }
            }
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SIGNIN) {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).result
            account?.let {
                googleAuthForFirebase(it)
            }
        }
    }

    private fun googleAuthForFirebase(account: GoogleSignInAccount) {
        val crediential = GoogleAuthProvider.getCredential(account.idToken, null)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                auth.signInWithCredential(crediential).await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Success", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun signInWithGmail() {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { results ->
            if (results.resultCode == Activity.RESULT_OK) {
//                val data: Intent? = results.data
                val task = GoogleSignIn.getSignedInAccountFromIntent(results.data)
                if (task.isSuccessful) {
                    val account: GoogleSignInAccount? = task.result
                    account?.let {
                        //get credentials
                        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                        auth.signInWithCredential(credential).addOnCompleteListener {
                            if (it.isSuccessful) {
//                                   val intent: Intent
                                Toast.makeText(
                                    this,
                                    "google auth success, 2nd method",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(this, it.exception.toString(), Toast.LENGTH_LONG)
                                    .show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, task.exception.toString(), Toast.LENGTH_LONG).show()
                }
            }
        }


    override fun onDestroy() {
        Firebase.auth.signOut()
        super.onDestroy()
    }

    private fun signInWithEmailAndPassword(email: String, password: String) {
        Toast.makeText(this, "Login clicked", Toast.LENGTH_SHORT).show()
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    val userLogged = LoggedInUserView(user?.displayName ?: "no name")
                    Toast.makeText(this, "success, $user", Toast.LENGTH_SHORT).show()
                    updateUiWithUser(userLogged)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                    showLoginFailed(0)
                }
            }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Toast.makeText(applicationContext, "user signed in $currentUser", Toast.LENGTH_SHORT)
                .show()
//                    updateUI(currentUser);
        }
        // signInWithFireBaseUI()

    }

    private fun signInWithFireBaseUI() {
        if (Firebase.auth.currentUser == null) {
            // Sign in with FirebaseUI, see docs for more details:
            // https://firebase.google.com/docs/auth/android/firebaseui
            val signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                // .setLogo(R.mipmap.ic_launcher)
                .setAvailableProviders(
                    listOf(
                        AuthUI.IdpConfig.EmailBuilder().build(),
                        AuthUI.IdpConfig.GoogleBuilder().build(),
                        AuthUI.IdpConfig.FacebookBuilder().build(),
                        AuthUI.IdpConfig.GitHubBuilder().build(),
                    )
                )
                .build()

            //  signIn.launch(signInIntent)
        } else {
//            goToMainActivity()
        }
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Sign in successful!")
            Toast.makeText(
                this,
                "Signing in Successful",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "There was an error signing in",
                Toast.LENGTH_LONG
            ).show()

            val response = result.idpResponse
            if (response == null) {
                Log.w(TAG, "Sign in canceled")
            } else {
                Log.w(TAG, "Sign in error", response.error)
            }
        }
    }


    private fun updateUiWithUser(model: LoggedInUserView) {
        val welcome = getString(R.string.welcome)
        // initiate successful logged in experience
        val user = Firebase.auth.currentUser
        user?.let {
            // Name, email address, and profile photo Url
            val name = it.displayName
            val email = it.email
            val photoUrl = it.photoUrl

            // Check if user's email is verified
            val emailVerified = it.isEmailVerified

            // The user's ID, unique to the Firebase project. Do NOT use this value to
            // authenticate with your backend server, if you have one. Use
            // FirebaseUser.getIdToken() instead.
            val uid = it.uid
        }
        Toast.makeText(
            applicationContext,
            "$welcome $user",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, "Error $errorString", Toast.LENGTH_SHORT).show()

    }

}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}

